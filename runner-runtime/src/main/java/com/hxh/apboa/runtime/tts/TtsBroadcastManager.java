package com.hxh.apboa.runtime.tts;

import com.hxh.apboa.common.cluster.core.BinaryChannelSubscriber;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.tts.TtsAudioFrame;
import com.hxh.apboa.common.tts.TtsCtrlMessage;
import com.hxh.apboa.common.tts.TtsStreamEvent;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.tts.stream.TtsAudioListener;
import com.hxh.apboa.engine.tts.stream.TtsStreamProviderHolder;
import com.hxh.apboa.engine.tts.stream.TtsStreamSession;
import io.agentscope.core.agui.event.AguiEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 语音播报编排：把 LLM 事件流与合成会话、订阅控制、音频出口桥接起来。
 *
 * - 控制面：订阅 tts:ctrl（websocket 服务转发的前端订阅/退订）维护播报白名单，
 *   懒合成——无人订阅的 run 不建会话不烧算力；
 * - 数据面：AGUI 事件分叉进来（onAguiEvent），白名单命中的 thread 建会话逐句合成；
 *   为所有活跃 run 保留正文累积缓冲，订阅晚于正文开始时从头补喂（feeder 全量
 *   累积语义天然支持）；
 * - 出口：会话音频回调封帧发 Redis（事件走 tts:event、PCM 走 tts:audio 二进制频道）。
 *
 * 会话生命周期绑定 run：一轮回复多段正文共用一个会话（音色/韵律锚定同一参考），
 * RunFinished 自然收尾（含错误路径——错误也以 RunFinished 收场，已产出句子播完）。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsBroadcastManager implements BinaryChannelSubscriber {

    private final TtsService ttsService;
    private final TtsStreamProviderHolder ttsStreamProviderHolder;
    private final MessagePublisher messagePublisher;
    private final TtsAudioCache ttsAudioCache;

    /** 播报目标（agent + 租户上下文，DB 访问发生在无请求上下文的线程，需随身携带） */
    private record BroadcastTarget(Long agentId, Long tenantId) {
    }

    /** 播报白名单：threadId → 播报目标（由订阅控制维护） */
    private final Map<String, BroadcastTarget> whitelist = new ConcurrentHashMap<>();

    /** 活跃 run 的正文累积（无论是否有人订阅都记录，供迟到订阅补喂；run 结束清理） */
    private final Map<String, StringBuilder> runBuffers = new ConcurrentHashMap<>();

    /** 活跃合成会话：threadId → 会话 */
    private final Map<String, TtsStreamSession> sessions = new ConcurrentHashMap<>();

    // ==================== 控制面（Redis tts:ctrl） ====================

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.TTS_CTRL_CHANNEL);
    }

    /**
     * 控制消息走单线程二进制容器（body 自行转字符串）：退订/重订连发产生的
     * close/open 必须按序处理——多线程池并发消费会乱序（close 分支重、常后完成），
     * 白名单被后到的 close 清空后播报永久哑火。与音频帧同容器还统一了相对顺序。
     */
    @Override
    public void onMessage(String channel, byte[] body) {
        String message = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        TtsCtrlMessage ctrl = JsonUtils.parse(message, TtsCtrlMessage.class);
        if (ctrl == null || ctrl.getThreadId() == null) {
            return;
        }
        if (TtsCtrlMessage.ACTION_OPEN.equals(ctrl.getAction()) && ctrl.getAgentId() != null) {
            whitelist.put(ctrl.getThreadId(), new BroadcastTarget(ctrl.getAgentId(), ctrl.getTenantId()));
            // run 已在进行：在 buffer 锁内建会话并从头补喂（与增量喂入互斥，保证会话
            // 收到的永远是 buffer 的严格前缀序列，不乱序不缺段）
            StringBuilder buffer = runBuffers.get(ctrl.getThreadId());
            if (buffer != null) {
                synchronized (buffer) {
                    openSessionWithBackfill(ctrl.getThreadId(), buffer);
                }
            }
            log.info("TTS 播报开启：threadId={}, agentId={}", ctrl.getThreadId(), ctrl.getAgentId());
        } else if (TtsCtrlMessage.ACTION_CLOSE.equals(ctrl.getAction())) {
            whitelist.remove(ctrl.getThreadId());
            TtsStreamSession session = sessions.remove(ctrl.getThreadId());
            if (session != null) {
                session.abort();
            }
            log.info("TTS 播报关闭：threadId={}", ctrl.getThreadId());
        }
    }

    // ==================== 数据面（AGUI 事件分叉） ====================

    /**
     * AGUI 事件分叉入口（在事件流消费处调用）。
     * 任何异常自吞——播报是旁路能力，绝不能影响主对话流。
     */
    public void onAguiEvent(AguiEvent event) {
        try {
            if (event instanceof AguiEvent.RunStarted started) {
                onRunStarted(started.threadId());
            } else if (event instanceof AguiEvent.TextMessageContent content) {
                onText(content.threadId(), content.delta());
            } else if (event instanceof AguiEvent.TextMessageEnd end) {
                onSegmentEnd(end.threadId());
            } else if (event instanceof AguiEvent.RunFinished finished) {
                onRunFinished(finished.threadId());
            }
        } catch (Exception e) {
            log.warn("TTS 播报处理 AGUI 事件失败（旁路忽略）: {}", e.getMessage());
        }
    }

    private void onRunStarted(String threadId) {
        runBuffers.put(threadId, new StringBuilder());
        // 上一轮的残留会话（异常未收尾）推平，本轮重新开始
        TtsStreamSession stale = sessions.remove(threadId);
        if (stale != null) {
            stale.abort();
        }
    }

    private void onText(String threadId, String delta) {
        StringBuilder buffer = runBuffers.get(threadId);
        if (buffer == null) {
            return;
        }
        synchronized (buffer) {
            buffer.append(delta);
            if (!whitelist.containsKey(threadId)) {
                return;
            }
            TtsStreamSession session = sessions.get(threadId);
            if (session != null) {
                session.feedText(delta);
            } else {
                openSessionWithBackfill(threadId, buffer);
            }
        }
    }

    /**
     * 段结束（TextMessageEnd，段间通常隔着工具调用）：换行入缓冲防补喂粘段，
     * 并显式冲刷会话尾句。仅喂换行不可靠——换行在段尾即提纯文本末尾，
     * 会被 feeder 的尾部 trim 剥掉，段尾无标点的尾句会滞留到下段开播才吐出。
     */
    private void onSegmentEnd(String threadId) {
        StringBuilder buffer = runBuffers.get(threadId);
        if (buffer == null) {
            return;
        }
        synchronized (buffer) {
            buffer.append('\n');
            TtsStreamSession session = sessions.get(threadId);
            if (session != null) {
                // 换行同步喂入保持「会话收到的是 buffer 严格前缀」的补喂不变量
                session.feedText("\n");
                session.flushSegment();
            }
        }
    }

    private void onRunFinished(String threadId) {
        runBuffers.remove(threadId);
        TtsStreamSession session = sessions.get(threadId);
        if (session != null) {
            session.finishText();
        }
    }

    // ==================== 会话与音频出口 ====================

    /**
     * 建会话并把 buffer 已累积正文一次性补喂（调用方须持有该 buffer 的锁）。
     * 创建失败发 error 事件并摘除白名单（配置类错误重试无益，避免每个 delta 重试一次）。
     */
    private void openSessionWithBackfill(String threadId, StringBuilder buffer) {
        if (sessions.containsKey(threadId)) {
            return;
        }
        BroadcastTarget target = whitelist.get(threadId);
        if (target == null) {
            return;
        }
        try {
            // 本方法运行在事件流/Redis 监听线程，无请求上下文——恢复订阅时随身携带的租户，
            // 否则租户插件会把 agent/模型查询过滤成空（参照 WebSocketPushController 先例）
            if (target.tenantId() != null) {
                TenantUtils.setCurrentTenant(target.tenantId(), null);
            }
            ModelConfigWrapper config = ttsService.resolveConfigWrapper(target.agentId());
            TtsStreamSession session = ttsStreamProviderHolder.get(config.getProvider())
                    .openSession(config, new RedisAudioListener(threadId));
            sessions.put(threadId, session);
            String accumulated = buffer.toString();
            if (!accumulated.isEmpty()) {
                session.feedText(accumulated);
            }
            log.info("TTS 会话建立：threadId={}, 补喂 {} 字", threadId, accumulated.length());
        } catch (Exception e) {
            log.warn("TTS 会话创建失败：threadId={}, agentId={}, error={}", threadId, target.agentId(), e.getMessage());
            publishEvent(TtsStreamEvent.builder()
                    .event(TtsStreamEvent.EVENT_ERROR).threadId(threadId).message(e.getMessage()).build());
            whitelist.remove(threadId);
        } finally {
            if (target.tenantId() != null) {
                TenantUtils.clear();
            }
        }
    }

    /**
     * 会话音频出口：事件走文本频道、PCM 封帧走二进制频道。
     * 回调发生在会话合成线程，publish 为非阻塞轻操作。
     */
    private class RedisAudioListener implements TtsAudioListener {

        private final String threadId;
        private final AtomicInteger seq = new AtomicInteger();

        RedisAudioListener(String threadId) {
            this.threadId = threadId;
        }

        @Override
        public void onFormat(int sampleRate, int bitsPerSample, int channels) {
            publishEvent(TtsStreamEvent.builder()
                    .event(TtsStreamEvent.EVENT_START).threadId(threadId)
                    .sampleRate(sampleRate).bitsPerSample(bitsPerSample).channels(channels)
                    .build());
        }

        @Override
        public void onAudioChunk(byte[] pcm) {
            byte[] frame = new TtsAudioFrame(threadId, seq.getAndIncrement(), pcm).encode();
            messagePublisher.publishBinary(RedisChannelTopic.TTS_AUDIO_CHANNEL, frame);
        }

        @Override
        public void onEnd() {
            sessions.remove(threadId);
            publishEvent(TtsStreamEvent.builder()
                    .event(TtsStreamEvent.EVENT_END).threadId(threadId).build());
        }

        @Override
        public void onError(String message) {
            sessions.remove(threadId);
            // 合成中途失败大多是引擎/配置问题，摘除白名单避免后续 delta 无限重建会话
            whitelist.remove(threadId);
            publishEvent(TtsStreamEvent.builder()
                    .event(TtsStreamEvent.EVENT_ERROR).threadId(threadId).message(message).build());
        }
    }

    private void publishEvent(TtsStreamEvent event) {
        messagePublisher.publish(RedisChannelTopic.TTS_EVENT_CHANNEL, JsonUtils.toJsonStr(event));
    }

    // ==================== 手动朗读（HTTP 触发的会话版播报） ====================

    /**
     * 朗读一段给定文本（历史消息正文），音频从该 thread 的订阅通道流出。
     * 要求前端已订阅该 threadId；朗读会打断该 thread 上的进行中播报。
     */
    public void broadcastText(String threadId, Long agentId, String markdown) {
        TtsStreamSession stale = sessions.remove(threadId);
        if (stale != null) {
            stale.abort();
        }
        whitelist.putIfAbsent(threadId, new BroadcastTarget(agentId, TenantUtils.getCurrentTenantId()));

        // 缓存命中：直接把缓存音频推回该 thread，不建会话、不调 TTS 引擎（省 GPU/RAM，
        // 后端全局共享——两台设备/同会话反复点同一条都命中）
        Long cfgId = ttsService.getTtsModelConfigId(agentId);
        // key 按 agentId 隔离（cfgId 仅用于判断是否绑定 TTS）：同一模型被不同 agent 覆盖不同音色时不串味
        String cacheKey = cfgId == null ? null : ttsAudioCache.buildKey(agentId, markdown);
        if (cacheKey != null) {
            TtsAudioCache.Entry hit = ttsAudioCache.get(cacheKey);
            if (hit != null) {
                replayCached(threadId, hit);
                log.info("TTS 手动朗读命中缓存：threadId={}, {} 字", threadId, markdown.length());
                return;
            }
        }

        // 未命中：现有逐句合成链路一字不改，仅把音频出口换成带缓存写入的装饰器
        ModelConfigWrapper config = ttsService.resolveConfigWrapper(agentId);
        TtsAudioListener listener = cacheKey == null
                ? new RedisAudioListener(threadId)
                : new CachingAudioListener(new RedisAudioListener(threadId), cacheKey);
        TtsStreamSession session = ttsStreamProviderHolder.get(config.getProvider())
                .openSession(config, listener);
        sessions.put(threadId, session);
        session.feedText(markdown);
        session.finishText();
        log.info("TTS 手动朗读：threadId={}, {} 字", threadId, markdown.length());
    }

    /**
     * 命中缓存回放：用 RedisAudioListener 把缓存 PCM 按格式分块推给该 thread，
     * 前端收到与实时合成同构的 start / PCM 帧 / end 序列，无感。
     */
    private void replayCached(String threadId, TtsAudioCache.Entry entry) {
        RedisAudioListener listener = new RedisAudioListener(threadId);
        listener.onFormat(entry.sampleRate(), entry.bitsPerSample(), entry.channels());
        byte[] pcm = ttsAudioCache.decodePcm(entry);
        // 分块推，避免单个超大二进制帧（16KB ≈ 0.33s @ 24kHz/16bit 单声道）
        final int chunkSize = 16000;
        for (int off = 0; off < pcm.length; off += chunkSize) {
            int end = Math.min(off + chunkSize, pcm.length);
            listener.onAudioChunk(java.util.Arrays.copyOfRange(pcm, off, end));
        }
        listener.onEnd();
    }

    /**
     * 缓存写入装饰器：透传给底层 listener 照常推帧（合成/播放链路不受影响），
     * 同时累积整段裸 PCM，onEnd 正常收尾时落缓存（onError / 中断不落）。
     */
    private class CachingAudioListener implements TtsAudioListener {

        private final TtsAudioListener delegate;
        private final String cacheKey;
        private final java.io.ByteArrayOutputStream pcmBuffer = new java.io.ByteArrayOutputStream();
        private int sampleRate;
        private int bitsPerSample;
        private int channels;
        private boolean failed = false;

        CachingAudioListener(TtsAudioListener delegate, String cacheKey) {
            this.delegate = delegate;
            this.cacheKey = cacheKey;
        }

        @Override
        public void onFormat(int sampleRate, int bitsPerSample, int channels) {
            this.sampleRate = sampleRate;
            this.bitsPerSample = bitsPerSample;
            this.channels = channels;
            delegate.onFormat(sampleRate, bitsPerSample, channels);
        }

        @Override
        public void onAudioChunk(byte[] pcm) {
            pcmBuffer.writeBytes(pcm);
            delegate.onAudioChunk(pcm);
        }

        @Override
        public void onEnd() {
            delegate.onEnd();
            if (!failed && sampleRate > 0 && pcmBuffer.size() > 0) {
                try {
                    ttsAudioCache.put(cacheKey, sampleRate, bitsPerSample, channels, pcmBuffer.toByteArray());
                } catch (Exception e) {
                    log.warn("TTS 缓存写入失败（忽略）: {}", e.getMessage());
                }
            }
        }

        @Override
        public void onError(String message) {
            failed = true;
            delegate.onError(message);
        }
    }
}

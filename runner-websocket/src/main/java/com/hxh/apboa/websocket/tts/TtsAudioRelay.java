package com.hxh.apboa.websocket.tts;

import com.hxh.apboa.common.cluster.core.BinaryChannelSubscriber;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.tts.TtsAudioFrame;
import com.hxh.apboa.websocket.config.ApboaWebSocketSessionManager;
import com.hxh.apboa.websocket.context.ApboaWebSocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TTS 音频帧中转：订阅 Redis 二进制音频频道，peek 路由头后把帧原样
 * 转发给订阅该 thread 的前端连接（零重编码）。无人订阅的帧直接丢弃
 * （runtime 会话由控制频道关闭，这里只兜竞态窗口内的余帧）。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsAudioRelay implements BinaryChannelSubscriber {

    private final TtsSubscriptionRegistry registry;

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.TTS_AUDIO_CHANNEL);
    }

    @Override
    public void onMessage(String channel, byte[] body) {
        String threadId = TtsAudioFrame.peekThreadId(body);
        if (threadId == null) {
            log.warn("收到无法解析路由头的 TTS 音频帧，丢弃（{} 字节）", body == null ? 0 : body.length);
            return;
        }
        List<ApboaWebSocketSession> sessions = registry.subscribers(threadId);
        for (ApboaWebSocketSession session : sessions) {
            if (session.writeable()) {
                ApboaWebSocketSessionManager.sendBinaryBySession(session, body);
            }
        }
    }
}

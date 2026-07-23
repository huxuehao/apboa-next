package com.hxh.apboa.runtime.tts;

import com.hxh.apboa.common.util.CryptoUtils;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.engine.tts.stream.SpeechSentenceFeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 手动朗读音频缓存（后端全局共享）。
 *
 * 同一 TTS 模型配置 + 同一段朗读文本的合成结果是确定的，缓存复用可避免重复调用
 * TTS 引擎（省 GPU/RAM）。放后端而非前端，是为了跨设备/跨会话共享——两台电脑、
 * 同一会话反复点同一条,首次合成后其余全部命中。
 *
 * key = tts:cache:{agentId}:{md5(提纯后朗读文本)}；
 * value = 完整裸 PCM 的 base64 + 播放所需格式（与 onFormat 的 sampleRate/bits/channels 一致）。
 * TTL 1 天。仅服务手动朗读（整条消息级）；自动播报按整个 run 多段拼接、粒度不同，不入此缓存。
 */
@Component
@RequiredArgsConstructor
public class TtsAudioCache {

    private final RedisUtils redisUtils;

    private static final String KEY_PREFIX = "tts:cache:";
    private static final long TTL_DAYS = 1;

    /** 缓存条目：裸 PCM 的 base64 + 播放格式（字段与 TtsAudioListener.onFormat 参数对应） */
    public record Entry(int sampleRate, int bitsPerSample, int channels, String pcmBase64) {
    }

    /**
     * 构造缓存 key。文本先经 SpeechSentenceFeeder.toSpeechText 提纯（与合成侧同一套提纯），
     * markdown 的格式符号被抹平，同内容不同写法也能命中。
     * 按 agentId 隔离：同一 TTS 模型被不同 agent 覆盖不同音色时互不串味（代价：跨 agent 不共享、
     * 同 agent 改音色后 1 天 TTL 内仍命中旧音色——按「音色缓存从简」取舍）。
     */
    public String buildKey(Long agentId, String markdown) {
        String speech = SpeechSentenceFeeder.toSpeechText(markdown == null ? "" : markdown);
        return KEY_PREFIX + agentId + ":" + CryptoUtils.md5(speech);
    }

    /** 命中返回条目，未命中返回 null */
    public Entry get(String key) {
        return redisUtils.get(key, Entry.class);
    }

    /** 写入缓存（1 天 TTL）。pcm 为整段裸 PCM */
    public void put(String key, int sampleRate, int bitsPerSample, int channels, byte[] pcm) {
        Entry entry = new Entry(sampleRate, bitsPerSample, channels,
                Base64.getEncoder().encodeToString(pcm));
        redisUtils.setEx(key, entry, TTL_DAYS, TimeUnit.DAYS);
    }

    /** 还原缓存条目里的裸 PCM */
    public byte[] decodePcm(Entry entry) {
        return Base64.getDecoder().decode(entry.pcmBase64());
    }
}

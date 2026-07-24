package com.hxh.apboa.engine.tts.stream;

/**
 * 语音合成会话的音频输出回调。
 * 会话产出的音频统一为裸 PCM（不含 WAV 容器头），格式经 onFormat 声明一次。
 * 实现方（WS 通道等）不得在回调里做耗时阻塞——回调发生在会话的合成线程上。
 *
 * @author huxuehao
 */
public interface TtsAudioListener {

    /**
     * 首块音频前回调一次，声明整个会话的流格式
     */
    void onFormat(int sampleRate, int bitsPerSample, int channels);

    /**
     * 一段 PCM 音频（一个已合成句子的样本数据）
     */
    void onAudioChunk(byte[] pcm);

    /**
     * 会话正常结束（finishText 后全部句子合成完毕）
     */
    void onEnd();

    /**
     * 会话异常终止（此后不再有任何回调）
     */
    void onError(String message);
}

package com.hxh.apboa.engine.tts;

/**
 * 语音合成结果：音频字节 + MIME 类型（audio/wav、audio/mpeg 等，供 HTTP 响应头使用）
 *
 * @author huxuehao
 */
public record TtsResult(byte[] audio, String mimeType) {
}

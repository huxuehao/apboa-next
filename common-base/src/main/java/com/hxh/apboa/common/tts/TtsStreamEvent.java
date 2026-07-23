package com.hxh.apboa.common.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TTS 流事件（runtime → websocket 服务，经 TTS_EVENT_CHANNEL；
 * websocket 服务原样作为 TTS_STREAM 类型 WsServerMessage 的 content 转发给前端）
 *
 * @author huxuehao
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TtsStreamEvent {

    public static final String EVENT_START = "start";
    public static final String EVENT_END = "end";
    public static final String EVENT_ERROR = "error";

    /** start / end / error */
    private String event;

    private String threadId;

    /** start 事件携带的音频格式 */
    private Integer sampleRate;
    private Integer bitsPerSample;
    private Integer channels;

    /** error 事件的失败原因 */
    private String message;
}

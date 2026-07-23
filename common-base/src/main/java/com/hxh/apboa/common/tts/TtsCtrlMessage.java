package com.hxh.apboa.common.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TTS 播报控制消息（websocket 服务 → runtime，经 TTS_CTRL_CHANNEL）
 *
 * @author huxuehao
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TtsCtrlMessage {

    public static final String ACTION_OPEN = "open";
    public static final String ACTION_CLOSE = "close";

    /** open=订阅（可开合成会话）/ close=退订（打断并释放） */
    private String action;

    private String threadId;

    /** 订阅时由前端携带，runtime 据此定位 TTS 模型绑定 */
    private Long agentId;

    private Long userId;

    private Long tenantId;
}

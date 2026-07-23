package com.hxh.apboa.runtime.tts;

import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.engine.tts.TtsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 描述：语音合成 Controller——AI 回复正文的语音播报（前端按句分段请求）
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/runtime/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsService ttsService;
    private final TtsBroadcastManager ttsBroadcastManager;

    /**
     * 合成请求体：agentId 定位 TTS 模型绑定，text 为已提纯的单段纯文本
     */
    public record SpeakRequest(Long agentId, String text) {
    }

    /**
     * 文字转语音：返回音频二进制（Content-Type 标明实际格式）；
     * 失败走全局异常处理器返回 JSON，前端按响应类型区分。音频即产即回不落盘。
     */
    @SkAccess
    @ChatKeyAccess
    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(@RequestBody SpeakRequest request) {
        TtsResult result = ttsService.speak(request.agentId(), request.text());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.mimeType()))
                .body(result.audio());
    }

    /**
     * 朗读请求体：threadId 定位音频流出的订阅通道（前端须已订阅），
     * text 为消息正文 markdown 原文（提纯与断句在会话内完成）
     */
    public record BroadcastRequest(String threadId, Long agentId, String text) {
    }

    /**
     * 手动朗读（历史消息等）：走流式会话通道，音频经 WS 订阅流回前端。
     * 会打断该 thread 上进行中的播报（同一时刻一路播报的全局语义）。
     */
    @SkAccess
    @ChatKeyAccess
    @PostMapping("/broadcast")
    public R<Void> broadcast(@RequestBody BroadcastRequest request) {
        if (request.threadId() == null || request.threadId().isBlank()
                || request.agentId() == null
                || request.text() == null || request.text().isBlank()) {
            throw new RuntimeException("threadId/agentId/text 均不可为空");
        }
        ttsBroadcastManager.broadcastText(request.threadId(), request.agentId(), request.text());
        return R.success("已开始朗读");
    }
}

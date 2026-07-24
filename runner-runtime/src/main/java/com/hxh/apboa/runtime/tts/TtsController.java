package com.hxh.apboa.runtime.tts;

import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.r.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    private final VoiceCatalogClient voiceCatalogClient;

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

    /**
     * 本地克隆 TTS 的音色列表（配置页下拉用）：转发 {baseUrl}/voices 私有协议接口。
     * baseUrl 为前端正在配置的 TTS 服务地址；需后台登录（不加免登注解）。
     */
    @GetMapping("/voices")
    public R<List<VoiceCatalogClient.VoiceItem>> voices(@RequestParam("baseUrl") String baseUrl) {
        return R.data(voiceCatalogClient.list(baseUrl));
    }
}

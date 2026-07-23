package com.hxh.apboa.runtime.asr;

import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.r.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 描述：语音识别 Controller——聊天输入框的语音转文字（一期整段识别）
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/runtime/asr")
@RequiredArgsConstructor
public class AsrController {

    private final AsrService asrService;

    /**
     * 语音转文字：接收整段 WAV，返回转写文字；音频即抛不落盘
     */
    @SkAccess
    @ChatKeyAccess
    @PostMapping("/recognize")
    public R<String> recognize(@RequestParam("agentId") Long agentId,
                               @RequestParam("file") MultipartFile file) {
        return R.data(asrService.recognize(agentId, file), "识别成功");
    }
}

package com.hxh.apboa.engine.asr;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;

/**
 * 语音识别（ASR）提供商接口：输入整段 WAV 音频，返回转写文字。
 * 新增提供商只需实现此接口并添加 @Component 即可自动注册（参照 EmbeddingProvider 模式）。
 *
 * @author huxuehao
 */
public interface AsrProvider {

    /**
     * 本实现服务的模型供应商类型
     */
    ModelProviderType getType();

    /**
     * 执行语音识别（一期仅接收 16bit PCM WAV，时长校验由上层完成）
     *
     * @param config   已装配 apiKey/baseUrl/modelCode 的模型配置
     * @param audioWav WAV 音频字节
     * @return 转写文字（已剥离模型特有前缀）
     */
    String recognize(ModelConfigWrapper config, byte[] audioWav);

    /**
     * Qwen3-ASR 系列的输出形如 "language Chinese&lt;asr_text&gt;正文"，
     * 含 &lt;asr_text&gt; 标记则取其后正文，否则原文返回（whisper/FunASR 等无前缀模型天然兼容）
     */
    default String stripAsrPrefix(String text) {
        if (text == null) {
            return "";
        }
        int idx = text.indexOf("<asr_text>");
        return idx >= 0 ? text.substring(idx + "<asr_text>".length()).trim() : text.trim();
    }
}

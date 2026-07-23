package com.hxh.apboa.engine.tts;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;

/**
 * 语音合成（TTS）提供商接口：输入一段文本，返回合成音频。
 * 新增提供商只需实现此接口并添加 @Component 即可自动注册（参照 AsrProvider 模式）。
 * 音色/语速/音频格式等供应商差异参数不建模为方法入参，统一经模型配置的
 * extendConfig.bodyParams 透传（如 OpenAI 兼容的 voice/speed、Qwen3-TTS 的 instruct）。
 *
 * @author huxuehao
 */
public interface TtsProvider {

    /**
     * 本实现服务的模型供应商类型
     */
    ModelProviderType getType();

    /**
     * 执行语音合成（单段文本，长度上限校验由上层完成）
     *
     * @param config 已装配 apiKey/baseUrl/modelCode/bodyParams 的模型配置
     * @param text   待合成的纯文本
     * @return 合成音频与 MIME 类型
     */
    TtsResult synthesize(ModelConfigWrapper config, String text);
}

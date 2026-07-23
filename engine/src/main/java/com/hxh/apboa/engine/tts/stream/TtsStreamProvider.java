package com.hxh.apboa.engine.tts.stream;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;

/**
 * 流式语音合成提供商：按供应商开启合成会话（参照 TtsProvider 的注册模式）。
 * 与单段合成的 TtsProvider 并存——单段接口保留给连通性检测等一次性场景，
 * 正文播报一律走会话。
 *
 * @author huxuehao
 */
public interface TtsStreamProvider {

    /**
     * 本实现服务的模型供应商类型
     */
    ModelProviderType getType();

    /**
     * 开启一个合成会话。音色等参数经 config 的 bodyParams 在会话内恒定生效。
     *
     * @param config   已装配 apiKey/baseUrl/modelCode/bodyParams 的模型配置
     * @param listener 音频输出回调
     */
    TtsStreamSession openSession(ModelConfigWrapper config, TtsAudioListener listener);
}

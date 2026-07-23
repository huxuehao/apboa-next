package com.hxh.apboa.engine.tts.stream;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.tts.DashScopeTtsProvider;
import com.hxh.apboa.engine.tts.TtsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DashScope（阿里百炼 / Qwen-TTS）的逐句流式合成：单段合成走 DashScopeTtsProvider
 * （qwen3-tts 的 multimodal-generation 同步接口，返回 WAV），会话生命周期由基类承担。
 * 使绑定 Qwen TTS 的智能体也能走 broadcast 流式播报，与本地 OPEN_AI 实现同一套架构
 * （逐句同步合成 + PCM 流水线），前端与播放器零改。
 *
 * @author huxuehao
 */
@Component
@RequiredArgsConstructor
public class DashScopeTtsStreamProvider extends AbstractSentenceTtsStreamProvider {

    private final DashScopeTtsProvider dashScopeTtsProvider;

    @Override
    public ModelProviderType getType() {
        return ModelProviderType.DASH_SCOPE;
    }

    @Override
    protected TtsResult synthesizeSentence(ModelConfigWrapper config, String sentence) {
        return dashScopeTtsProvider.synthesize(config, sentence);
    }
}

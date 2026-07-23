package com.hxh.apboa.engine.tts.stream;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.tts.OpenAiCompatTtsProvider;
import com.hxh.apboa.engine.tts.TtsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容服务（本地 mlx-audio 等）的逐句流式合成：单段合成走 OpenAiCompatTtsProvider，
 * 会话生命周期（切句 / 串行 / 中断 / 收尾）由 AbstractSentenceTtsStreamProvider 承担。
 *
 * @author huxuehao
 */
@Component
@RequiredArgsConstructor
public class LocalTtsStreamProvider extends AbstractSentenceTtsStreamProvider {

    private final OpenAiCompatTtsProvider openAiCompatTtsProvider;

    @Override
    public ModelProviderType getType() {
        return ModelProviderType.OPEN_AI;
    }

    @Override
    protected TtsResult synthesizeSentence(ModelConfigWrapper config, String sentence) {
        return openAiCompatTtsProvider.synthesize(config, sentence);
    }
}

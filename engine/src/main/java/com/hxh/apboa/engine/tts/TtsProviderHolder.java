package com.hxh.apboa.engine.tts;

import com.hxh.apboa.common.enums.ModelProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * TtsProvider 注册与分发：收集容器内全部实现，按供应商类型路由（参照 AsrProviderHolder 模式）。
 *
 * @author huxuehao
 */
@Component
public class TtsProviderHolder {

    private static final Logger log = LoggerFactory.getLogger(TtsProviderHolder.class);

    private final Map<ModelProviderType, TtsProvider> providerMap = new EnumMap<>(ModelProviderType.class);

    public TtsProviderHolder(List<TtsProvider> providers) {
        for (TtsProvider provider : providers) {
            providerMap.put(provider.getType(), provider);
            log.info("注册TtsProvider: {} -> {}", provider.getType(), provider.getClass().getSimpleName());
        }
    }

    /**
     * 按供应商类型取实现，未注册的供应商（如 OLLAMA 不支持语音合成）明确报错
     */
    public TtsProvider get(ModelProviderType type) {
        TtsProvider provider = providerMap.get(type);
        if (provider == null) {
            throw new RuntimeException("该模型供应商不支持语音合成: " + type);
        }
        return provider;
    }
}

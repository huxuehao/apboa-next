package com.hxh.apboa.engine.tts.stream;

import com.hxh.apboa.common.enums.ModelProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * TtsStreamProvider 注册与分发：收集容器内全部实现，按供应商类型路由
 * （参照 TtsProviderHolder 模式）。
 *
 * @author huxuehao
 */
@Component
public class TtsStreamProviderHolder {

    private static final Logger log = LoggerFactory.getLogger(TtsStreamProviderHolder.class);

    private final Map<ModelProviderType, TtsStreamProvider> providerMap = new EnumMap<>(ModelProviderType.class);

    public TtsStreamProviderHolder(List<TtsStreamProvider> providers) {
        for (TtsStreamProvider provider : providers) {
            providerMap.put(provider.getType(), provider);
            log.info("注册TtsStreamProvider: {} -> {}", provider.getType(), provider.getClass().getSimpleName());
        }
    }

    /**
     * 按供应商类型取实现，未注册的供应商明确报错
     */
    public TtsStreamProvider get(ModelProviderType type) {
        TtsStreamProvider provider = providerMap.get(type);
        if (provider == null) {
            throw new RuntimeException("该模型供应商不支持流式语音合成: " + type);
        }
        return provider;
    }
}

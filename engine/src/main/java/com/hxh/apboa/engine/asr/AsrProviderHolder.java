package com.hxh.apboa.engine.asr;

import com.hxh.apboa.common.enums.ModelProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AsrProvider 注册与分发：收集容器内全部实现，按供应商类型路由（参照 EmbeddingService 模式）。
 *
 * @author huxuehao
 */
@Component
public class AsrProviderHolder {

    private static final Logger log = LoggerFactory.getLogger(AsrProviderHolder.class);

    private final Map<ModelProviderType, AsrProvider> providerMap = new EnumMap<>(ModelProviderType.class);

    public AsrProviderHolder(List<AsrProvider> providers) {
        for (AsrProvider provider : providers) {
            providerMap.put(provider.getType(), provider);
            log.info("注册AsrProvider: {} -> {}", provider.getType(), provider.getClass().getSimpleName());
        }
    }

    /**
     * 按供应商类型取实现，未注册的供应商（如 OLLAMA 不支持音频输入）明确报错
     */
    public AsrProvider get(ModelProviderType type) {
        AsrProvider provider = providerMap.get(type);
        if (provider == null) {
            throw new RuntimeException("该模型供应商不支持语音识别: " + type);
        }
        return provider;
    }
}

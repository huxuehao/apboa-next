package com.hxh.apboa.engine.model.impl;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.model.IChatModel;
import com.hxh.apboa.engine.model.GenerateOptionsHelper;
import com.hxh.apboa.engine.model.HttpTransportHelper;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.formatter.dashscope.DashScopeMultiAgentFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.EndpointType;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;

/**
 * 描述：DashScope 模型
 *
 * @author huxuehao
 **/
@Component
public class DefaultDashScopeModelI implements IChatModel {
    @Override
    public Model getModel(ModelConfigWrapper config) {
        if (config.getProvider() != getProvider()) {
            throw new IllegalArgumentException("The provider is not supported");
        }

        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelCode())
                .stream(config.getStreaming() != null && config.getStreaming())
                .enableThinking(config.getThinking() != null && config.getThinking())
                .httpTransport(HttpTransportHelper.createOkHttpTransport())
                .defaultOptions(GenerateOptionsHelper.create(config));

        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            builder.baseUrl(config.getBaseUrl());
        }

        if (config.isMulti()) {
            builder.formatter(new DashScopeMultiAgentFormatter());
        } else {
            builder.formatter(new DashScopeChatFormatter());
        }
        if (isMultimodalModel(config.getModelCode())) {
            builder.endpointType(EndpointType.MULTIMODAL);
            builder.formatter(new DashScopeChatFormatter());
        }

        return builder.build();
    }

    @Override
    public Model getSimpleModel(ModelConfigWrapper config) {
        if (config.getProvider() != getProvider()) {
            throw new IllegalArgumentException("The provider is not supported");
        }

        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelCode())
                .stream(false)
                .httpTransport(HttpTransportHelper.createOkHttpTransport(10, 15));

        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            builder.baseUrl(config.getBaseUrl());
        }
        if (isMultimodalModel(config.getModelCode())) {
            builder.endpointType(EndpointType.MULTIMODAL);
            builder.formatter(new DashScopeChatFormatter());
        }

        return builder.build();
    }

    @Override
    public ModelProviderType getProvider() {
        return ModelProviderType.DASH_SCOPE;
    }

    @Override
    public int order() {
        return 0;
    }
    public static boolean isMultimodalModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        String lowerModelName = modelName.toLowerCase();
        return lowerModelName.startsWith("qvq")
                || lowerModelName.contains("-vl")
                || lowerModelName.contains("-asr")
                || lowerModelName.startsWith("qwen3.5")
                || lowerModelName.startsWith("qwen3.6")
                || lowerModelName.startsWith("qwen3.7")
                || lowerModelName.contains("kimi-k2.5")
                || lowerModelName.contains("kimi-k2.6");
    }
}

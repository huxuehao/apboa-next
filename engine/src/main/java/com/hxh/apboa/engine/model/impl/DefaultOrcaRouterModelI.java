package com.hxh.apboa.engine.model.impl;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.formatter.FixedSysMsgOpenAIChatFormatter;
import com.hxh.apboa.engine.formatter.FixedSysMsgOpenAIMultiAgentFormatter;
import com.hxh.apboa.engine.model.IChatModel;
import com.hxh.apboa.engine.model.GenerateOptionsHelper;
import com.hxh.apboa.engine.model.HttpTransportHelper;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.formatter.openai.OpenAIMultiAgentFormatter;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.stereotype.Component;

/**
 * 描述：OrcaRouter 模型
 *
 * @author huxuehao
 **/
@Component
public class DefaultOrcaRouterModelI implements IChatModel {
    private static final String ORCA_ROUTER_BASE_URL = "https://api.orcarouter.ai/v1";

    @Override
    public Model getModel(ModelConfigWrapper config) {
        if (config.getProvider() != getProvider()) {
            throw new IllegalArgumentException("The provider is not supported");
        }

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelCode())
                .stream(config.getStreaming() != null && config.getStreaming())
                .httpTransport(HttpTransportHelper.createOkHttpTransport())
                .generateOptions(GenerateOptionsHelper.create(config));

        builder.baseUrl(effectiveBaseUrl(config));

        if (config.isMulti()) {
            if (config.getFixedSystemMessage() != null && config.getFixedSystemMessage()) {
                builder.formatter(new FixedSysMsgOpenAIMultiAgentFormatter());
            } else {
                builder.formatter(new OpenAIMultiAgentFormatter());
            }

        } else {
            if (config.getFixedSystemMessage() != null && config.getFixedSystemMessage()) {
                builder.formatter(new FixedSysMsgOpenAIChatFormatter());
            } else {
                builder.formatter(new OpenAIChatFormatter());
            }
        }

        return builder.build();
    }

    @Override
    public Model getSimpleModel(ModelConfigWrapper config) {
        if (config.getProvider() != getProvider()) {
            throw new IllegalArgumentException("The provider is not supported");
        }

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelCode())
                .stream(config.getStreaming() != null && config.getStreaming())
                .httpTransport(HttpTransportHelper.createOkHttpTransport(10, 15));

        builder.baseUrl(effectiveBaseUrl(config));

        return builder.build();
    }

    @Override
    public ModelProviderType getProvider() {
        return ModelProviderType.ORCA_ROUTER;
    }

    @Override
    public int order() {
        return 0;
    }

    /**
     * OrcaRouter 默认走官方网关地址；未显式配置 baseUrl 时兜底到默认值
     */
    private String effectiveBaseUrl(ModelConfigWrapper config) {
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            return config.getBaseUrl();
        }
        return ORCA_ROUTER_BASE_URL;
    }
}

package com.hxh.apboa.engine.model;

import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.util.ExtendConfigHelper;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.common.wrapper.ModelWrapper;
import com.hxh.apboa.model.service.ModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 描述： 聊天模型工厂
 *
 * @author huxuehao
 **/
@Component
public class ChatModelFactory {
    private static final Map<ModelProviderType, IChatModel> MODEL_MAP = new ConcurrentHashMap<>();

    private final ModelConfigService modelConfigService;

    public ChatModelFactory(List<IChatModel> IChatModels, ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
        IChatModels.stream()
                .collect(Collectors.groupingBy(IChatModel::getProvider))
                .forEach((provider, models) -> {
                    // 降序
                    models.sort((o1, o2) -> o2.order() - o1.order());
                    // 获取优先级最高的实现
                    MODEL_MAP.put(provider, models.getFirst());
                });
    }

    /**
     * 获取模型
     *
     * @param agentDefinition ModelConfigWrapper
     * @return 模型
     */
    public Model getModel(AgentDefinition agentDefinition) {
        return getModel(agentDefinition, false);
    }

    /**
     * 获取模型
     *
     * @param agentDefinition ModelConfigWrapper
     * @return 模型
     */
    public Model getModel(AgentDefinition agentDefinition, boolean multi) {
        ModelConfigWrapper configWrapper = ModelConfigWrapper.builder().build();

        // 获取agent中配置的参数
        JsonNode modelParamsOverride = agentDefinition.getModelParamsOverride();
        if (modelParamsOverride != null && !modelParamsOverride.isEmpty()) {
            if (modelParamsOverride.has("temperature")) {
                configWrapper.setTemperature(modelParamsOverride.get("temperature").asDouble());
            }
            if (modelParamsOverride.has("topP")) {
                configWrapper.setTopP(modelParamsOverride.get("topP").asDouble());
            }
            if (modelParamsOverride.has("topK")) {
                configWrapper.setTopK(modelParamsOverride.get("topK").asInt());
            }
            if (modelParamsOverride.has("maxTokens")) {
                configWrapper.setMaxTokens(modelParamsOverride.get("maxTokens").asInt());
            }
            if (modelParamsOverride.has("repeatPenalty")) {
                configWrapper.setRepeatPenalty(modelParamsOverride.get("repeatPenalty").asDouble());
            }
            if (modelParamsOverride.has("streaming")) {
                configWrapper.setStreaming(modelParamsOverride.get("streaming").asBoolean());
            }
            if (modelParamsOverride.has("seed")) {
                configWrapper.setSeed(modelParamsOverride.get("seed").asLong());
            }
            // 解析 extendConfig 填充 headers、queryParams、bodyParams（agent 级覆盖优先）
            JsonNode extendConfig = modelParamsOverride.get("extendConfig");
            if (extendConfig != null && !extendConfig.isNull() && extendConfig.isObject()) {
                ExtendConfigHelper.fillOverride(configWrapper, extendConfig);
            }
        }

        ModelWrapper config = modelConfigService.getModelWrapperById(agentDefinition.getModelConfigId());
        // 填充模型配置
        config.getConfig().fillModelConfigWrapper(configWrapper);
        // 填充供应商配置
        config.getProvider().fillModelConfigWrapper(configWrapper);
        configWrapper.setMulti(multi);
        configWrapper.setToolChoiceStrategy(agentDefinition.getToolChoiceStrategy());
        configWrapper.setSpecificToolName(agentDefinition.getSpecificToolName());

        // 会话级思考模式：按 provider 分两套翻译，开关变化由 AguiRequestProcessor 检测触发 agent 重建，
        // 本处在重建时读到新值。DASH_SCOPE 走百炼官方 enable_thinking（下面设 thinking 布尔，
        // DefaultDashScopeModelI 消费）；OPEN_AI 走数据驱动的 thinkingParams（见后一分支）。
        // thinking = 会话覆盖 ?? 默认开；非会话场景（无 threadId）覆盖为 null 即默认开。
        if (configWrapper.getProvider() == ModelProviderType.DASH_SCOPE) {
            String threadId = AgentContext.getIfExists().map(AgentContext::getThreadId).orElse(null);
            if (threadId != null && !threadId.isEmpty()) {
                Boolean override = ThinkingModeResolver.resolveOverride(threadId);
                configWrapper.setThinking(override == null || override);
            }
        }

        // OPEN_AI（含本地 Ollama /v1）：思考控制无 SDK 原生开关，靠把 thinkingParams 里
        // 用户声明的请求体参数按开/关状态 merge 进 bodyParams（代码不认识 reasoning_effort 等具体名）。
        // 仅 thinking==true（模型标记支持）时生效。新建 HashMap 避免 bodyParams 为不可变空 Map 时 putAll 抛错。
        if (configWrapper.getProvider() == ModelProviderType.OPEN_AI
                && Boolean.TRUE.equals(configWrapper.getThinking())) {
            String threadId = AgentContext.getIfExists().map(AgentContext::getThreadId).orElse(null);
            Boolean override = ThinkingModeResolver.resolveOverride(threadId);
            boolean on = override == null || override;
            Map<String, Object> pick = on ? configWrapper.getThinkingParamsOn() : configWrapper.getThinkingParamsOff();
            if (pick != null && !pick.isEmpty()) {
                Map<String, Object> merged = new HashMap<>();
                if (configWrapper.getBodyParams() != null) {
                    merged.putAll(configWrapper.getBodyParams());
                }
                merged.putAll(pick);
                configWrapper.setBodyParams(merged);
            }
        }

        IChatModel IChatModel = MODEL_MAP.get(configWrapper.getProvider());
        if (IChatModel == null) {
            throw new RuntimeException("No chat model found for provider " + configWrapper.getProvider());
        }

        return IChatModel.getModel(configWrapper);
    }

    /**
     * 获取简单的模型
     *
     * @param configWrapper 模型配置
     * @return 模型
     */
    public Model getSimpleModel(ModelConfigWrapper configWrapper) {
        IChatModel IChatModel = MODEL_MAP.get(configWrapper.getProvider());
        if (IChatModel == null) {
            throw new RuntimeException("No chat model found for provider " + configWrapper.getProvider());
        }

        return IChatModel.getSimpleModel(configWrapper);
    }
}

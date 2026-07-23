package com.hxh.apboa.engine.model;

import com.hxh.apboa.agent.service.AgentModelConfigService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.util.ExtendConfigHelper;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.common.wrapper.ModelWrapper;
import com.hxh.apboa.model.service.ModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
public class ChatModelFactory {
    private static final Map<ModelProviderType, IChatModel> MODEL_MAP = new ConcurrentHashMap<>();

    private final ModelConfigService modelConfigService;
    private final AgentModelConfigService agentModelConfigService;

    public ChatModelFactory(List<IChatModel> IChatModels, ModelConfigService modelConfigService,
                            AgentModelConfigService agentModelConfigService) {
        this.modelConfigService = modelConfigService;
        this.agentModelConfigService = agentModelConfigService;
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

        // 会话级模型覆盖（前台在候选集内切换，Redis 实时写入，agent 重建时此处读到新值）：
        // 覆盖必须仍是该 agent 的额外候选且模型可用，失效（解绑/禁用后的旧覆盖）静默回落默认，不断对话。
        // 先解析本次实际使用的模型，参数覆盖再按归属选取
        Long modelConfigIdToUse = agentDefinition.getModelConfigId();
        Long modelOverride = SessionModelResolver.resolveOverride(
                AgentContext.getIfExists().map(AgentContext::getThreadId).orElse(null));
        if (modelOverride != null && !modelOverride.equals(modelConfigIdToUse)) {
            if (isUsableCandidate(agentDefinition.getId(), modelOverride)) {
                modelConfigIdToUse = modelOverride;
            } else {
                log.warn("会话模型覆盖已失效，回落默认模型: agentId={}, override={}",
                        agentDefinition.getId(), modelOverride);
            }
        }

        // 参数覆盖按归属：默认模型用 agent 级 model_params_override，
        // 候选模型用其关联行的 model_params_override（per-model；null=跟随模型自身默认）
        JsonNode modelParamsOverride = modelConfigIdToUse.equals(agentDefinition.getModelConfigId())
                ? agentDefinition.getModelParamsOverride()
                : agentModelConfigService.getParamsOverride(agentDefinition.getId(), modelConfigIdToUse);
        Boolean nodeThinkingOverride = booleanOverride(modelParamsOverride, "thinking");
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

        ModelWrapper config = modelConfigService.getModelWrapperById(modelConfigIdToUse);
        // 记录本次实际选定的模型（消息级审计：ReActAgentHelper 搬进 AgentMetadataStore，
        // ChatLogHook 落 meta / Adapter 下发 RUN_META 按 agentId 读取）
        final Long selectedModelId = modelConfigIdToUse;
        AgentContext.getIfExists().ifPresent(ctx -> {
            ctx.setActiveModelConfigId(selectedModelId);
            ctx.setActiveModelLabel(config.getConfig() != null ? config.getConfig().getName() : null);
        });
        // 填充模型配置
        config.getConfig().fillModelConfigWrapper(configWrapper);
        // 填充供应商配置
        config.getProvider().fillModelConfigWrapper(configWrapper);
        configWrapper.setMulti(multi);
        configWrapper.setToolChoiceStrategy(agentDefinition.getToolChoiceStrategy());
        configWrapper.setSpecificToolName(agentDefinition.getSpecificToolName());

        // 节点级快捷开关优先于会话覆盖；两者都未设置时跟随支持思考模型的默认开启状态。
        // DASH_SCOPE 直接消费 thinking 布尔，OPEN_AI 则在后一分支选择 thinkingParams.on/off。
        String threadId = AgentContext.getIfExists().map(AgentContext::getThreadId).orElse(null);
        Boolean sessionThinkingOverride = ThinkingModeResolver.resolveOverride(threadId);
        boolean thinkingEnabled = resolveThinkingEnabled(
                configWrapper.getThinking(), nodeThinkingOverride, sessionThinkingOverride);
        if (configWrapper.getProvider() == ModelProviderType.DASH_SCOPE
                && Boolean.TRUE.equals(configWrapper.getThinking())) {
            configWrapper.setThinking(thinkingEnabled);
        }

        // OPEN_AI（含本地 Ollama /v1）：思考控制无 SDK 原生开关，靠把 thinkingParams 里
        // 用户声明的请求体参数按开/关状态 merge 进 bodyParams（代码不认识 reasoning_effort 等具体名）。
        // 仅 thinking==true（模型标记支持）时生效。新建 HashMap 避免 bodyParams 为不可变空 Map 时 putAll 抛错。
        if (configWrapper.getProvider() == ModelProviderType.OPEN_AI
                && Boolean.TRUE.equals(configWrapper.getThinking())) {
            Map<String, Object> pick = thinkingEnabled
                    ? configWrapper.getThinkingParamsOn()
                    : configWrapper.getThinkingParamsOff();
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

    static boolean resolveThinkingEnabled(Boolean modelSupportsThinking,
                                          Boolean nodeOverride,
                                          Boolean sessionOverride) {
        if (!Boolean.TRUE.equals(modelSupportsThinking)) {
            return false;
        }
        if (nodeOverride != null) {
            return nodeOverride;
        }
        return sessionOverride == null || sessionOverride;
    }

    private static Boolean booleanOverride(JsonNode params, String field) {
        if (params == null || !params.has(field) || params.get(field).isNull()) {
            return null;
        }
        return params.get(field).asBoolean();
    }

    /**
     * 覆盖目标是否可用的候选：仍在关联表候选集内、模型存在且启用。
     * （覆盖值等于默认模型时上游直接跳过，不走本判断）
     */
    private boolean isUsableCandidate(Long agentId, Long modelConfigId) {
        try {
            if (!agentModelConfigService.getModelIds(agentId).contains(modelConfigId)) {
                return false;
            }
            ModelConfig candidate = modelConfigService.getById(modelConfigId);
            return candidate != null && Boolean.TRUE.equals(candidate.getEnabled());
        } catch (Exception e) {
            return false;
        }
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

package com.hxh.apboa.engine.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.agent.service.AgentCodeExecutionService;
import com.hxh.apboa.agent.service.CodeExecutionConfigService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.CodeExecutionConfig;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.wrapper.KnowledgeWrapper;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.hook.HooksFactory;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.knowledge.KnowledgeFactory;
import com.hxh.apboa.engine.model.ChatModelFactory;
import com.hxh.apboa.engine.prompt.AgentSysPromptFactory;
import com.hxh.apboa.engine.skill.SkillBoxFactory;
import com.hxh.apboa.engine.memory.LongTermMemoryFactory;
import com.hxh.apboa.engine.studio.StudioService;
import com.hxh.apboa.engine.tool.ToolkitFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.StatePersistence;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：ReAct智能体Helper
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class ReActAgentHelper {
    private final HooksFactory hooksFactory;
    private final ChatModelFactory chatModelFactory;
    private final AgentSysPromptFactory agentSysPromptFactory;
    private final SkillBoxFactory skillBoxFactory;
    private final ToolkitFactory toolkitFactory;
    private final KnowledgeFactory knowledgeFactory;
    private final StudioService studioService;
    private final LongTermMemoryFactory longTermMemoryFactory;
    private final AgentCodeExecutionService agentCodeExecutionService;
    private final CodeExecutionConfigService codeExecutionConfigService;

    /**
     * 获取 ReActAgent
     * @param definition agent 定义
     */
    public ReActAgent getReActAgent(AgentDefinition definition) {
        return getReActAgent(definition, false);
    }

    /**
     * 获取 ReActAgent
     *
     * @param definition  agent 定义
     * @param asSubAgent 是否作为子智能体构建：子智能体运行在主 agent 的一次工具调用内部，
     *                   其 HITL 确认暂停无法冒泡到主事件流（主流会一直等工具返回，会话卡死），
     *                   故一律剥离确认 Hook（授权语义收敛在主 agent 层）。
     *                   TODO: 支持子智能体确认冒泡到主流后，允许用户配置子智能体内工具是否需确认
     */
    public ReActAgent getReActAgent(AgentDefinition definition, boolean asSubAgent) {
        // 构建reActAgent
        return getReactAgentBuilder(definition, asSubAgent).build();
    }

    /**
     * 获取 ReActAgent.Builder
     * @param definition  agent 定义
     */
    public ReActAgent.Builder getReactAgentBuilder(AgentDefinition definition) {
        return getReactAgentBuilder(definition, false);
    }

    /**
     * 获取 ReActAgent.Builder
     *
     * @param definition  agent 定义
     * @param asSubAgent 是否作为子智能体构建（剥离确认 Hook，见 getReActAgent 重载说明）
     */
    public ReActAgent.Builder getReactAgentBuilder(AgentDefinition definition, boolean asSubAgent) {
        Model model = chatModelFactory.getModel(definition);
        Toolkit toolkit = toolkitFactory.getToolkit(definition);
        CodeExecutionConfig codeExecutionConfig = getCodeExecutionConfig(definition.getId());
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(definition.getAgentCode())
                .description(FuncUtils.isEmpty(definition.getDescription()) ? definition.getName() : definition.getDescription())
                .maxIters(definition.getMaxIterations())
                .model(model)
                .sysPrompt(agentSysPromptFactory.getAgentSysPrompt(definition, codeExecutionConfig != null))
                .toolkit(toolkit)
                .skillBox(toolkit != null
                        ? skillBoxFactory.getSkillBox(definition, toolkit, codeExecutionConfig)
                        : skillBoxFactory.getSkillBox(definition, codeExecutionConfig));

        KnowledgeWrapper knowledgeWrapper = knowledgeFactory.getKnowledge(definition);
        if (knowledgeWrapper != null) {
            builder.knowledge(knowledgeWrapper.getKnowledge());
            builder.ragMode(knowledgeWrapper.getRagMode());

            JsonNode retrievalConfigNode = knowledgeWrapper.getRetrievalConfig();
            int limit = JsonUtils.getIntValue(retrievalConfigNode, "topK", 5);
            double scoreThreshold = JsonUtils.getDoubleValue(retrievalConfigNode, "scoreThreshold", 0.5);
            builder.retrieveConfig(
                    RetrieveConfig.builder()
                            .limit(limit)
                            .scoreThreshold(scoreThreshold)
                            .build());
        }

        Boolean isPlanActive = AgentContext.getIfExists().map(AgentContext::isPlanActive).orElse(false);
        if (definition.getEnablePlanning() && isPlanActive) {
            PlanNotebook planNotebook = PlanNotebook.builder()
                    .maxSubtasks(definition.getMaxSubtasks())
                    .needUserConfirm(definition.getRequirePlanConfirmation())
                    .build();
            builder.planNotebook(planNotebook);
        }

        // 使用可变列表，避免 getHooks 返回 List.of() 时 add 抛 UnsupportedOperationException
        List<Hook> hooks = hooksFactory.getHooks(definition);
        hooks = hooks != null ? new ArrayList<>(hooks) : new ArrayList<>();

        // 子智能体一律免确认（原因与 TODO 见方法 javadoc）
        if (asSubAgent) {
            hooks.removeIf(hook -> hook instanceof IConfirmationHook);
        }

        // 配置记忆
        Boolean isMemoryActive = AgentContext.getIfExists().map(AgentContext::isMemoryActive).orElse(false);
        if (definition.getEnableMemory() && isMemoryActive) {
            if (definition.getEnableMemoryCompression()) {
                JsonNode config = definition.getMemoryCompressionConfig();
                AutoContextConfig autoContextConfig = AutoContextConfig.builder()
                        .maxToken(JsonUtils.getLongValue(config, "maxToken", 131072L))
                        .msgThreshold(JsonUtils.getIntValue(config, "msgThreshold", 100))
                        .lastKeep(JsonUtils.getIntValue(config, "lastKeep", 50))
                        .tokenRatio(JsonUtils.getDoubleValue(config, "tokenRatio", 0.75F))
                        .minCompressionTokenThreshold(JsonUtils.getIntValue(config, "minCompressionTokenThreshold", 5000))
                        .currentRoundCompressionRatio(JsonUtils.getDoubleValue(config, "currentRoundCompressionRatio", 0.3))
                        .minConsecutiveToolMessages(JsonUtils.getIntValue(config, "minConsecutiveToolMessages", 6))
                        .offloadSinglePreview(JsonUtils.getIntValue(config, "offloadSinglePreview", 200))
                        .largePayloadThreshold(JsonUtils.getLongValue(config, "largePayloadThreshold", 5120L))
                        .build();
                builder.memory(new AutoContextMemory(autoContextConfig, model));
                hooks.add(new AutoContextHook());
            } else {
                builder.memory(new InMemoryMemory());
            }
        }

        // HITL（docs/hitl-confirmation-refactor.md §6.1）：无条件开启「待确认工具恢复」+ 状态持久化，
        // 让工具暂停态可被 saveTo/loadFrom，且不依赖 memoryActive（修 §2.5 Bug3：不开记忆时确认也不能失效）。
        // 暂停态恢复必须连 memory 一起持久化（V0 实测：仅 statefulTools 会因缺 user query 被模型拒绝），
        // 故 memoryManaged 恒为 true；长期记忆「是否保留」交由 AguiRequestProcessor/resume 的 session 生命周期控制。
        builder.enablePendingToolRecovery(true);
        builder.statePersistence(
                StatePersistence.builder()
                        .memoryManaged(true)
                        .statefulToolsManaged(true)
                        .planNotebookManaged(definition.getEnablePlanning() && isPlanActive)
                        .build());

        // 配置长期记忆
        LongTermMemory longTermMemory = longTermMemoryFactory.createLongTermMemory(definition);
        if (longTermMemory != null) {
            builder.longTermMemory(longTermMemory);
            LongTermMemoryMode memoryMode = longTermMemoryFactory.getMemoryMode(definition);
            if (memoryMode != null) {
                builder.longTermMemoryMode(memoryMode);
            }
            // 异步记录长期记忆，避免阻塞主流程
            builder.longTermMemoryAsyncRecord(true);
        }

        // 配置Studio
        if (studioService.init(definition)) {
            hooks.add(new StudioMessageHook(StudioManager.getClient()));
        }

        // 添加Hook
        if (!hooks.isEmpty()) {
            builder.hooks(hooks);
        }

        // 结构化输出
        if (definition.getStructuredOutputEnabled()) {
            builder.structuredOutputReminder(definition.getStructuredOutputReminder());
        }

        // 保存Agent定义到上下文
        AgentContext.get().setAgentDefinition(definition);

        // 注册工具执行上下文
        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(AgentContext.get())
                .build();
        builder.toolExecutionContext(context);

        return builder;
    }

    /**
     * 获取代码执行配置
     *
     * @param agentDefinitionId 智能体定义ID
     * @return 代码执行配置
     */
    private CodeExecutionConfig getCodeExecutionConfig(Long agentDefinitionId) {
        Long codeExecutionId = agentCodeExecutionService.getCodeExecutionIdByAgentId(agentDefinitionId);
        if (codeExecutionId == null) {
            return null;
        }
        return codeExecutionConfigService.getById(codeExecutionId);
    }
}

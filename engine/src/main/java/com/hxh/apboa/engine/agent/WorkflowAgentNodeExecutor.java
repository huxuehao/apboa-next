package com.hxh.apboa.engine.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.enums.ToolChoiceStrategy;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.log.telemetry.ChatChannelHolder;
import com.hxh.apboa.engine.log.telemetry.RunStatAccumulator;
import com.hxh.apboa.engine.log.telemetry.UsageRecordWriter;
import com.hxh.apboa.engine.log.telemetry.WorkflowUsageHook;
import com.hxh.apboa.engine.mcp.McpClientFactory;
import com.hxh.apboa.engine.model.ChatModelFactory;
import com.hxh.apboa.engine.model.WorkflowProgressModel;
import com.hxh.apboa.engine.skill.SkillBoxFactory;
import com.hxh.apboa.engine.tool.ToolkitFactory;
import com.hxh.apboa.engine.tool.ToolProgressBridge;
import com.hxh.apboa.node.agent.AgentNodeExecutor;
import com.hxh.apboa.node.agent.AgentModelRequestTrace;
import com.hxh.apboa.node.agent.AgentNodeRequest;
import com.hxh.apboa.node.agent.AgentNodeResult;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 工作流智能体节点执行器。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowAgentNodeExecutor implements AgentNodeExecutor {
    private final ChatModelFactory chatModelFactory;
    private final ToolkitFactory toolkitFactory;
    private final SkillBoxFactory skillBoxFactory;
    private final McpClientFactory mcpClientFactory;
    private final UsageRecordWriter usageRecordWriter;

    @Override
    public AgentNodeResult execute(AgentNodeRequest request) {
        ToolChoiceStrategy toolChoiceStrategy = resolveToolChoiceStrategy(request);
        Toolkit toolkit;
        SkillBox skillBox = null;
        if (toolChoiceStrategy == ToolChoiceStrategy.NONE) {
            // 禁止工具时不加载任何工具/MCP/技能，既缩短 prompt，也避免第三方 formatter 忽略 NONE。
            toolkit = new Toolkit();
        } else {
            toolkit = toolkitFactory.getToolkit(request.getToolIds());
            registerMcpTools(toolkit, request);
            skillBox = createSkillBox(request, toolkit);
        }
        int effectiveMaxIterations = resolveEffectiveMaxIterations(
                request,
                toolChoiceStrategy,
                hasCallableCapabilities(toolkit, skillBox));
        AgentDefinition definition = buildAgentDefinition(request);
        definition.setToolChoiceStrategy(toolChoiceStrategy);
        definition.setMaxIterations(effectiveMaxIterations);
        Model baseModel = chatModelFactory.getModel(definition);
        String progressToolUseId = ToolProgressBridge.currentToolUseId();
        // 始终包装以采集可落库遥测；非对话工具场景 toolUseId 为 null，进度发送自动降级为空操作。
        WorkflowProgressModel progressModel = new WorkflowProgressModel(
                baseModel,
                progressToolUseId,
                request.getNodeId(),
                request.getNodeName(),
                UUID.randomUUID().toString());
        Model model = progressModel;

        AgentContext oldContext = AgentContext.getIfExists().orElse(null);
        AgentContext agentContext = buildAgentContext(request, definition, oldContext);
        AgentContext.init(agentContext);
        try {
            WorkflowUsageHook usageHook = new WorkflowUsageHook();
            ReActAgent agent = buildAgent(
                    request,
                    definition,
                    model,
                    toolkit,
                    skillBox,
                    effectiveMaxIterations,
                    agentContext,
                    usageHook);
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .textContent(request.getUserPrompt())
                    .build();

            long startMs = System.currentTimeMillis();
            Msg response = request.isStructuredOutputEnabled()
                    ? agent.call(userMsg, request.getStructuredOutput()).block()
                    : agent.call(userMsg).block();

            // 执行抛异常不记账，与主链 CHAT 的 ErrorEvent 丢弃统计口径一致
            recordUsage(request, oldContext, agentContext, usageHook, response,
                    System.currentTimeMillis() - startMs);
            return buildResult(
                    model,
                    response,
                    toolChoiceStrategy,
                    effectiveMaxIterations,
                    progressModel,
                    usageHook);
        } finally {
            restoreAgentContext(oldContext);
        }
    }

    /**
     * 节点 run 记账（biz_type=WORKFLOW）：token 以 hook 逐轮累计为准（节点带工具多轮时
     * 最终 Msg 只含末轮 usage），hook 无累积时回落末轮 usage 兜底。
     *
     * <p>归属口径：对话内触发（外层有主 agent 上下文）计入主 agent 名下——这笔消耗由该
     * agent 的对话引发，其月预算应能管控到；渠道随主会话。独立/调试运行无 agent 归属
     * （agent_id=0、agent_label 记工作流名快照），渠道记 STANDALONE。
     */
    private void recordUsage(AgentNodeRequest request, AgentContext outerContext, AgentContext agentContext,
                             WorkflowUsageHook usageHook, Msg response, long durationMs) {
        try {
            RunStatAccumulator stat = usageHook.getAccumulator();
            long inputTokens;
            long outputTokens;
            int iterations;
            if (stat.hasData()) {
                inputTokens = stat.getInputTokens();
                outputTokens = stat.getOutputTokens();
                iterations = stat.getIterations();
            } else if (response != null && response.getChatUsage() != null) {
                ChatUsage usage = response.getChatUsage();
                inputTokens = usage.getInputTokens();
                outputTokens = usage.getOutputTokens();
                iterations = 1;
            } else {
                // 无任何用量证据（未发生推理）不落流水
                return;
            }

            // 租户优先取本节点上下文（buildAgentContext 里的 TenantUtils 值），独立运行线程无
            // ThreadLocal 时回落外层主 agent 上下文
            Long tenantId = agentContext.getTenantId() != null
                    ? agentContext.getTenantId()
                    : (outerContext != null ? outerContext.getTenantId() : null);
            Long mainAgentId = null;
            String agentLabel = request.getWorkflowName() != null
                    ? "工作流：" + request.getWorkflowName()
                    : "工作流";
            Long userId = null;
            Long sessionId = null;
            String channel = null;
            if (outerContext != null) {
                if (outerContext.getAgentDefinition() != null) {
                    mainAgentId = outerContext.getAgentDefinition().getId();
                }
                if (outerContext.getUserInfo() != null) {
                    userId = outerContext.getUserInfo().getId();
                }
                sessionId = parseChatSessionId(outerContext.getThreadId());
                channel = ChatChannelHolder.get(outerContext.getThreadId());
            } else {
                UserDetail userDetail = UserUtils.getUserDetail();
                userId = userDetail != null ? userDetail.getId() : null;
                // 独立/调试运行不是对话入口，给专属渠道值；置 NULL 会与
                // 渠道标记上线前的历史流水混在「未标记（历史）」一桶
                channel = SysConst.CHANNEL_STANDALONE;
            }
            // 触发渠道显式下传时优先（定时任务=SCHEDULED；经 run 入口→变量上下文→节点请求，
            // 不能反查 quartz_job_records——关联在 run 结束后才写，节点记账期读不到）
            if (request.getTriggerChannel() != null) {
                channel = request.getTriggerChannel();
            }
            // 定时执行线程无登录上下文（UserUtils 取不到），归属人从下传的任务创建人补齐
            if (userId == null && request.getTriggerUserId() != null) {
                userId = request.getTriggerUserId();
            }
            usageRecordWriter.writeWorkflowRun(tenantId, request.getWorkflowId(), request.getWorkflowInstanceId(),
                    request.getWorkflowName(), request.getNodeId(), request.getNodeName(), sessionId,
                    mainAgentId, agentLabel, userId,
                    request.getModelConfigId(), channel,
                    inputTokens, outputTokens, iterations, durationMs);
        } catch (Exception ex) {
            log.error("工作流节点成本记账失败 instanceId={} node={}: {}",
                    request.getWorkflowInstanceId(), request.getNodeId(), ex.getMessage());
        }
    }

    /** 非对话执行的 threadId 可能不是数字，无法可靠关联 chat_session 时保持为空。 */
    private Long parseChatSessionId(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(threadId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 构建最小智能体定义。
     */
    private AgentDefinition buildAgentDefinition(AgentNodeRequest request) {
        AgentDefinition definition = new AgentDefinition();
        definition.setName(request.getNodeName());
        definition.setAgentCode("workflow-agent-" + request.getNodeId());
        definition.setDescription(request.getNodeName());
        definition.setModelConfigId(request.getModelConfigId());
        JsonNode modelParamsOverride = mergeModelParamsOverride(request);
        if (modelParamsOverride != null) {
            definition.setModelParamsOverride(modelParamsOverride);
        }
        definition.setSystemPrompt(request.getSystemPrompt());
        definition.setMaxIterations(request.getMaxIterations());
        definition.setEnableMemory(false);
        definition.setEnablePlanning(false);
        definition.setStructuredOutputEnabled(request.isStructuredOutputEnabled());
        definition.setStructuredOutputReminder(StructuredOutputReminder.PROMPT);
        definition.setStructuredOutputSchema(request.getStructuredOutput());
        return definition;
    }

    /**
     * 合并高级参数覆盖与模型快捷开关。快捷开关不依赖“启用参数覆盖”，并覆盖同名旧字段；
     * 两个快捷值均为 null 且高级覆盖关闭时返回 null，使旧工作流继续跟随模型默认值。
     */
    static JsonNode mergeModelParamsOverride(AgentNodeRequest request) {
        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        JsonNode advanced = request.getModelParamsOverride();
        if (request.isModelParamsOverrideEnabled() && advanced != null && advanced.isObject()) {
            merged.setAll((ObjectNode) advanced);
        }
        if (request.getStreaming() != null) {
            merged.put("streaming", request.getStreaming());
        }
        if (request.getThinking() != null) {
            merged.put("thinking", request.getThinking());
        }
        return merged.isEmpty() ? null : merged;
    }

    /**
     * 注册节点配置中的MCP工具。
     */
    private void registerMcpTools(Toolkit toolkit, AgentNodeRequest request) {
        List<AgentTool> mcpTools = mcpClientFactory.getLazyMcpTools(request.getMcps());
        mcpTools.forEach(toolkit::registerAgentTool);
    }

    /** 空技能清单不构造 SkillBox，避免 AgentScope 自动注入不可调用的技能加载工具。 */
    SkillBox createSkillBox(AgentNodeRequest request, Toolkit toolkit) {
        if (!shouldCreateSkillBox(request)) {
            return null;
        }
        SkillBox skillBox = skillBoxFactory.getSkillBox(request.getSkillPackageIds(), toolkit);
        return skillBox == null || skillBox.getAllSkillIds().isEmpty() ? null : skillBox;
    }

    static boolean shouldCreateSkillBox(AgentNodeRequest request) {
        return request.getSkillPackageIds() != null && !request.getSkillPackageIds().isEmpty();
    }

    static ToolChoiceStrategy resolveToolChoiceStrategy(AgentNodeRequest request) {
        return request.getToolChoiceStrategy() == null
                ? ToolChoiceStrategy.AUTO
                : request.getToolChoiceStrategy();
    }

    static int resolveEffectiveMaxIterations(
            AgentNodeRequest request,
            ToolChoiceStrategy toolChoiceStrategy,
            boolean hasCallableCapabilities) {
        if (toolChoiceStrategy == ToolChoiceStrategy.NONE || !hasCallableCapabilities) {
            return 1;
        }
        return Math.max(1, request.getMaxIterations());
    }

    static boolean hasCallableCapabilities(Toolkit toolkit, SkillBox skillBox) {
        return (toolkit != null && !toolkit.getToolNames().isEmpty())
                || (skillBox != null && !skillBox.getAllSkillIds().isEmpty());
    }

    /**
     * 构建智能体调用上下文。
     */
    private AgentContext buildAgentContext(AgentNodeRequest request, AgentDefinition definition, AgentContext outerContext) {
        AgentContext agentContext = new AgentContext();
        agentContext.setThreadId(request.getWorkflowInstanceId());
        agentContext.setRunId(request.getWorkflowInstanceId());
        agentContext.setMemoryActive(false);
        agentContext.setPlanActive(false);
        agentContext.setParams(request.getInputs());
        agentContext.setAgentDefinition(definition);
        agentContext.setTenantId(TenantUtils.getCurrentTenantId());
        agentContext.setTenantCode(TenantUtils.getCurrentTenantCode());
        // 对话内触发时继承主 agent 的服务端认证身份，MCP 身份断言才能带上用户主体（sub）；
        // REST 直接触发无外层上下文，保持 null 按匿名断言处理
        if (outerContext != null) {
            agentContext.setUserInfo(outerContext.getUserInfo());
        }
        return agentContext;
    }

    /**
     * 构建无记忆、无代码执行的ReActAgent。
     */
    private ReActAgent buildAgent(AgentNodeRequest request,
                                  AgentDefinition definition,
                                  Model model,
                                  Toolkit toolkit,
                                  SkillBox skillBox,
                                  int effectiveMaxIterations,
                                  AgentContext agentContext,
                                  WorkflowUsageHook usageHook) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(definition.getAgentCode())
                .description(definition.getDescription())
                .maxIters(effectiveMaxIterations)
                .model(model)
                .sysPrompt(request.getSystemPrompt())
                .toolkit(toolkit)
                .hook(usageHook)
                .toolExecutionContext(ToolExecutionContext.builder()
                        .register(agentContext)
                        .build());

        if (skillBox != null) {
            builder.skillBox(skillBox);
        }

        if (request.isStructuredOutputEnabled()) {
            builder.structuredOutputReminder(StructuredOutputReminder.PROMPT);
        }
        return builder.build();
    }

    /**
     * 构建节点执行结果。
     */
    @SuppressWarnings("unchecked")
    private AgentNodeResult buildResult(
            Model model,
            Msg response,
            ToolChoiceStrategy toolChoiceStrategy,
            int effectiveMaxIterations,
            WorkflowProgressModel progressModel,
            WorkflowUsageHook usageHook) {
        AgentNodeResult result = new AgentNodeResult();
        // 展示名走平台配置映射（ChatModelFactory 构建时写入 ctx 的 ModelConfig.name），
        // 与消息 footer/成本流水的模型标签口径一致；无上下文时回落供应商模型 code
        String modelLabel = AgentContext.getIfExists()
                .map(AgentContext::getActiveModelLabel).orElse(null);
        result.setModelName(modelLabel != null && !modelLabel.isBlank() ? modelLabel : model.getModelName());
        result.setToolChoiceStrategy(toolChoiceStrategy);
        result.setEffectiveMaxIterations(effectiveMaxIterations);
        result.setModelRequests(buildModelRequestTraces(progressModel, usageHook));
        if (response == null) {
            return result;
        }
        result.setText(response.getTextContent());
        if (response.hasStructuredData()) {
            Object structuredOutputObj = response.getMetadata().get("_structured_output");
            if (structuredOutputObj instanceof String structuredOutputString) {
                result.setStructured(JsonUtils.parse(structuredOutputString, Map.class));
            } else  {
                ContentBlock first = response.getContent().getFirst();
                if (first instanceof TextBlock textBlock) {
                    String jsonStr = textBlock.getText();
                    if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                        jsonStr = jsonStr.substring(1, jsonStr.length() - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\");
                    }
                    result.setStructured(JsonUtils.parse(jsonStr, Map.class));
                }
            }
        }
        result.setUsage(response.getChatUsage());
        result.setGenerateReason(response.getGenerateReason());
        return result;
    }

    private List<AgentModelRequestTrace> buildModelRequestTraces(
            WorkflowProgressModel progressModel, WorkflowUsageHook usageHook) {
        Map<Integer, WorkflowProgressModel.RequestTiming> timings = new TreeMap<>();
        progressModel.snapshotRequestTimings()
                .forEach(item -> timings.put(item.requestIndex(), item));
        Map<Integer, WorkflowUsageHook.RoundTelemetry> rounds = new TreeMap<>();
        usageHook.snapshotRounds().forEach(item -> rounds.put(item.requestIndex(), item));

        Set<Integer> requestIndexes = new LinkedHashSet<>();
        requestIndexes.addAll(timings.keySet());
        requestIndexes.addAll(rounds.keySet());
        List<AgentModelRequestTrace> traces = new ArrayList<>();
        for (Integer requestIndex : requestIndexes) {
            WorkflowProgressModel.RequestTiming timing = timings.get(requestIndex);
            WorkflowUsageHook.RoundTelemetry round = rounds.get(requestIndex);
            List<AgentModelRequestTrace.Attempt> attempts = timing == null
                    ? List.of()
                    : timing.attempts().stream()
                            .map(item -> new AgentModelRequestTrace.Attempt(
                                    item.attempt(),
                                    item.status(),
                                    item.elapsed(),
                                    item.ttft(),
                                    item.detail()))
                            .toList();
            traces.add(new AgentModelRequestTrace(
                    requestIndex,
                    timing == null ? 1 : timing.maxAttempts(),
                    timing == null ? null : timing.status(),
                    timing == null ? null : timing.durationMs(),
                    timing == null ? null : timing.ttftMs(),
                    round == null ? null : round.inputTokens(),
                    round == null ? null : round.outputTokens(),
                    round == null ? null : round.totalTokens(),
                    round == null ? null : round.modelTimeSeconds(),
                    timing == null ? null : timing.finishReason(),
                    round == null ? null : round.generateReason(),
                    round == null ? null : round.thinkingChars(),
                    timing == null ? Map.of() : timing.providerMetrics(),
                    attempts,
                    round == null ? List.of() : round.toolCalls()));
        }
        return List.copyOf(traces);
    }

    /**
     * 恢复调用前的智能体上下文。
     */
    private void restoreAgentContext(AgentContext oldContext) {
        if (oldContext != null) {
            AgentContext.set(oldContext);
        } else {
            AgentContext.clean();
        }
    }
}

package com.hxh.apboa.engine.agent;

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
import com.hxh.apboa.engine.skill.SkillBoxFactory;
import com.hxh.apboa.engine.tool.ToolkitFactory;
import com.hxh.apboa.node.agent.AgentNodeExecutor;
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

import java.util.List;
import java.util.Map;

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
        AgentDefinition definition = buildAgentDefinition(request);
        definition.setToolChoiceStrategy(ToolChoiceStrategy.AUTO);
        Model model = chatModelFactory.getModel(definition);
        Toolkit toolkit = toolkitFactory.getToolkit(request.getToolIds());
        registerMcpTools(toolkit, request);
        SkillBox skillBox = skillBoxFactory.getSkillBox(request.getSkillPackageIds(), toolkit);

        AgentContext oldContext = AgentContext.getIfExists().orElse(null);
        AgentContext agentContext = buildAgentContext(request, definition, oldContext);
        AgentContext.init(agentContext);
        try {
            WorkflowUsageHook usageHook = new WorkflowUsageHook();
            ReActAgent agent = buildAgent(request, definition, model, toolkit, skillBox, agentContext, usageHook);
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
            return buildResult(model, response);
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
            String agentLabel = null;
            Long userId = null;
            String channel = null;
            if (outerContext != null) {
                if (outerContext.getAgentDefinition() != null) {
                    mainAgentId = outerContext.getAgentDefinition().getId();
                    agentLabel = outerContext.getAgentDefinition().getName();
                }
                if (outerContext.getUserInfo() != null) {
                    userId = outerContext.getUserInfo().getId();
                }
                channel = ChatChannelHolder.get(outerContext.getThreadId());
            } else {
                UserDetail userDetail = UserUtils.getUserDetail();
                userId = userDetail != null ? userDetail.getId() : null;
                // 独立/调试运行不是对话入口，给专属渠道值；置 NULL 会与
                // 渠道标记上线前的历史流水混在「未标记（历史）」一桶
                channel = SysConst.CHANNEL_STANDALONE;
            }
            if (mainAgentId == null) {
                agentLabel = request.getWorkflowName() != null ? "工作流：" + request.getWorkflowName() : "工作流";
            }

            usageRecordWriter.writeWorkflowRun(tenantId, request.getWorkflowInstanceId(),
                    mainAgentId, agentLabel, userId,
                    request.getModelConfigId(), channel,
                    inputTokens, outputTokens, iterations, durationMs);
        } catch (Exception ex) {
            log.error("工作流节点成本记账失败 instanceId={} node={}: {}",
                    request.getWorkflowInstanceId(), request.getNodeId(), ex.getMessage());
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
        if (request.isModelParamsOverrideEnabled()) {
            definition.setModelParamsOverride(request.getModelParamsOverride());
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
     * 注册节点配置中的MCP工具。
     */
    private void registerMcpTools(Toolkit toolkit, AgentNodeRequest request) {
        List<AgentTool> mcpTools = mcpClientFactory.getLazyMcpTools(request.getMcps());
        mcpTools.forEach(toolkit::registerAgentTool);
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
                                  AgentContext agentContext,
                                  WorkflowUsageHook usageHook) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(definition.getAgentCode())
                .description(definition.getDescription())
                .maxIters(request.getMaxIterations())
                .model(model)
                .sysPrompt(request.getSystemPrompt())
                .toolkit(toolkit)
                .skillBox(skillBox)
                .hook(usageHook)
                .toolExecutionContext(ToolExecutionContext.builder()
                        .register(agentContext)
                        .build());

        if (request.isStructuredOutputEnabled()) {
            builder.structuredOutputReminder(StructuredOutputReminder.PROMPT);
        }
        return builder.build();
    }

    /**
     * 构建节点执行结果。
     */
    @SuppressWarnings("unchecked")
    private AgentNodeResult buildResult(Model model, Msg response) {
        AgentNodeResult result = new AgentNodeResult();
        result.setModelName(model.getModelName());
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

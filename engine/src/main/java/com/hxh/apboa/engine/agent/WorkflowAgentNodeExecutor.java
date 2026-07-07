package com.hxh.apboa.engine.agent;

import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.enums.ToolChoiceStrategy;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.agui.AgentContext;
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
import io.agentscope.core.model.Model;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工作流智能体节点执行器。
 *
 * @author huxuehao
 */
@Component
@RequiredArgsConstructor
public class WorkflowAgentNodeExecutor implements AgentNodeExecutor {
    private final ChatModelFactory chatModelFactory;
    private final ToolkitFactory toolkitFactory;
    private final SkillBoxFactory skillBoxFactory;
    private final McpClientFactory mcpClientFactory;

    @Override
    public AgentNodeResult execute(AgentNodeRequest request) {
        AgentDefinition definition = buildAgentDefinition(request);
        definition.setToolChoiceStrategy(ToolChoiceStrategy.AUTO);
        Model model = chatModelFactory.getModel(definition);
        Toolkit toolkit = toolkitFactory.getToolkit(request.getToolIds());
        registerMcpTools(toolkit, request);
        SkillBox skillBox = skillBoxFactory.getSkillBox(request.getSkillPackageIds(), toolkit);

        AgentContext oldContext = AgentContext.getIfExists().orElse(null);
        AgentContext agentContext = buildAgentContext(request, definition);
        AgentContext.init(agentContext);
        try {
            ReActAgent agent = buildAgent(request, definition, model, toolkit, skillBox, agentContext);
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .textContent(request.getUserPrompt())
                    .build();

            Msg response = request.isStructuredOutputEnabled()
                    ? agent.call(userMsg, request.getStructuredOutput()).block()
                    : agent.call(userMsg).block();

            return buildResult(model, response);
        } finally {
            restoreAgentContext(oldContext);
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
    private AgentContext buildAgentContext(AgentNodeRequest request, AgentDefinition definition) {
        AgentContext agentContext = new AgentContext();
        agentContext.setThreadId(request.getWorkflowInstanceId());
        agentContext.setRunId(request.getWorkflowInstanceId());
        agentContext.setMemoryActive(false);
        agentContext.setPlanActive(false);
        agentContext.setParams(request.getInputs());
        agentContext.setAgentDefinition(definition);
        agentContext.setTenantId(TenantUtils.getCurrentTenantId());
        agentContext.setTenantCode(TenantUtils.getCurrentTenantCode());
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
                                  AgentContext agentContext) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(definition.getAgentCode())
                .description(definition.getDescription())
                .maxIters(request.getMaxIterations())
                .model(model)
                .sysPrompt(request.getSystemPrompt())
                .toolkit(toolkit)
                .skillBox(skillBox)
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

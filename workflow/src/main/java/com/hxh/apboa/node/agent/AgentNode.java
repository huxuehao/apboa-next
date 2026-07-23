package com.hxh.apboa.node.agent;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.enums.McpToolExposureMode;
import com.hxh.apboa.common.enums.ToolChoiceStrategy;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.template.FormatterType;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 智能体工作流节点。
 *
 * @author huxuehao
 */
public class AgentNode extends EnhancedNode {
    @Getter
    private final Config config;

    public AgentNode(String id, String name, Config config) {
        super(id, name, NodeType.AGENT);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output, context);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出。
     *
     * @param inputs  节点输入
     * @param output  节点输出
     * @param context 执行上下文
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        TemplateFormatter formatter = TemplateFormatterFactory.createFormatter(config.getFormatterType());
        String renderedSystemPrompt = renderPrompt(formatter, config.getSystemPrompt(), inputs);
        String renderedUserPrompt = renderPrompt(formatter, config.getUserPrompt(), inputs);

        AgentNodeExecutor executor = SpringContextHolder.getBean(AgentNodeExecutor.class);
        AgentNodeResult result = executor.execute(buildRequest(inputs, context, renderedSystemPrompt, renderedUserPrompt));

        Object defaultOutput = config.isStructuredOutputEnabled() && result.getStructured() != null
                ? result.getStructured()
                : result.getText();
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, defaultOutput);
        output.addOutput("text", result.getText());
        output.addOutput("structured", result.getStructured());

        output.addExecutionContext("modelConfigId", config.getModelConfigId());
        output.addExecutionContext("modelName", result.getModelName());
        output.addExecutionContext("maxIterations", config.getMaxIterations());
        output.addExecutionContext("structuredOutputEnabled", config.isStructuredOutputEnabled());
        output.addExecutionContext("skillPackageIds", config.getSkillPackageIds());
        output.addExecutionContext("toolIds", config.getToolIds());
        output.addExecutionContext("mcps", config.getMcps());
        output.addExecutionContext("toolChoiceStrategy", result.getToolChoiceStrategy());
        output.addExecutionContext("effectiveMaxIterations", result.getEffectiveMaxIterations());
        output.addExecutionContext("modelRequests", result.getModelRequests());
        output.addExecutionContext("usage", result.getUsage());
        output.addExecutionContext("generateReason", result.getGenerateReason());
        output.markComplete();
        return output;
    }

    /**
     * 渲染提示词。
     *
     * @param formatter 模板渲染器
     * @param prompt    原始提示词
     * @param inputs    节点输入
     * @return 渲染后的提示词
     */
    private String renderPrompt(TemplateFormatter formatter, String prompt, Map<String, Object> inputs) {
        if (prompt == null) {
            return "";
        }
        Object rendered = formatter.format(prompt, inputs, false);
        return rendered == null ? "" : rendered.toString();
    }

    /**
     * 构建智能体执行请求。
     */
    private AgentNodeRequest buildRequest(Map<String, Object> inputs,
                                          NodeContext context,
                                          String renderedSystemPrompt,
                                          String renderedUserPrompt) {
        AgentNodeRequest request = new AgentNodeRequest();
        Object workflowId = context.getVariables().getVariable("workflowId");
        request.setWorkflowId(workflowId != null ? workflowId.toString() : null);
        request.setWorkflowInstanceId(context.getWorkflowInstanceId());
        Object workflowName = context.getVariables().getVariable("workflowName");
        request.setWorkflowName(workflowName != null ? workflowName.toString() : null);
        Object triggerChannel = context.getVariables().getVariable("triggerChannel");
        request.setTriggerChannel(triggerChannel != null ? triggerChannel.toString() : null);
        Object userId = context.getVariables().getVariable("userId");
        if (userId instanceof Long id) {
            request.setTriggerUserId(id);
        } else if (userId != null) {
            try {
                request.setTriggerUserId(Long.parseLong(userId.toString()));
            } catch (NumberFormatException ignored) {
                // 变量被业务覆盖为非数值时放弃归属，不影响执行
            }
        }
        request.setNodeId(getId());
        request.setNodeName(getName());
        request.setModelConfigId(config.getModelConfigId());
        request.setStreaming(config.getStreaming());
        request.setThinking(config.getThinking());
        request.setModelParamsOverrideEnabled(config.isModelParamsOverrideEnabled());
        request.setModelParamsOverride(config.getModelParamsOverride());
        request.setSystemPrompt(renderedSystemPrompt);
        request.setUserPrompt(renderedUserPrompt);
        request.setSkillPackageIds(config.getSkillPackageIds());
        request.setToolIds(config.getToolIds());
        request.setMcps(config.getMcps());
        request.setToolChoiceStrategy(config.getToolChoiceStrategy());
        request.setMaxIterations(config.getMaxIterations());
        request.setStructuredOutputEnabled(config.isStructuredOutputEnabled());
        request.setStructuredOutput(config.getStructuredOutput());
        request.setInputs(inputs);
        return request;
    }

    /**
     * 创建异常输出。
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (config == null) {
            return VerifyResult.invalid(new VerifyFail("config", "智能体节点配置不能为空"));
        }
        if (config.getModelConfigId() == null) {
            return VerifyResult.invalid(new VerifyFail("modelConfigId", "模型配置ID不能为空"));
        }
        if (config.getFormatterType() == null) {
            return VerifyResult.invalid(new VerifyFail("formatterType", "提示词模板渲染方式不能为空"));
        }
        if (!List.of(FormatterType.STRING, FormatterType.VELOCITY).contains(config.getFormatterType())) {
            return VerifyResult.invalid(new VerifyFail("formatterType", "不支持的提示词模板渲染方式: " + config.getFormatterType()));
        }
        if (FuncUtils.isEmpty(config.getUserPrompt())) {
            return VerifyResult.invalid(new VerifyFail("userPrompt", "用户提示词不能为空"));
        }
        if (config.getMaxIterations() <= 0) {
            return VerifyResult.invalid(new VerifyFail("maxIterations", "最大迭代次数必须大于0"));
        }
        if (config.getToolChoiceStrategy() == null) {
            return VerifyResult.invalid(new VerifyFail("toolChoiceStrategy", "工具选择策略不能为空"));
        }
        if (!List.of(ToolChoiceStrategy.AUTO, ToolChoiceStrategy.NONE)
                .contains(config.getToolChoiceStrategy())) {
            return VerifyResult.invalid(new VerifyFail(
                    "toolChoiceStrategy", "工作流智能体仅支持 AUTO 或 NONE"));
        }
        if (config.isModelParamsOverrideEnabled()
                && (config.getModelParamsOverride() == null
                || !config.getModelParamsOverride().isObject()
                || config.getModelParamsOverride().isEmpty())) {
            return VerifyResult.invalid(new VerifyFail("modelParamsOverride", "参数覆盖配置不能为空"));
        }
        if (config.isStructuredOutputEnabled()
                && (config.getStructuredOutput() == null
                || !config.getStructuredOutput().isObject()
                || config.getStructuredOutput().isEmpty())) {
            return VerifyResult.invalid(new VerifyFail("structuredOutput", "结构化输出配置不能为空"));
        }

        VerifyResult listVerifyResult = verifyLists();
        if (!listVerifyResult.isValid()) {
            return listVerifyResult;
        }
        return VerifyResult.valid();
    }

    /**
     * 校验列表类配置。
     */
    private VerifyResult verifyLists() {
        if (containsNull(config.getSkillPackageIds())) {
            return VerifyResult.invalid(new VerifyFail("skillPackageIds", "技能包ID列表不能包含空值"));
        }
        if (containsNull(config.getToolIds())) {
            return VerifyResult.invalid(new VerifyFail("toolIds", "工具ID列表不能包含空值"));
        }
        if (config.getMcps() == null) {
            return VerifyResult.valid();
        }
        for (McpConfig mcp : config.getMcps()) {
            if (mcp == null || mcp.getMcpServerId() == null) {
                return VerifyResult.invalid(new VerifyFail("mcps.mcpServerId", "MCP服务ID不能为空"));
            }
            if (mcp.getExposureMode() == null) {
                return VerifyResult.invalid(new VerifyFail("mcps.exposureMode", "MCP工具暴露模式不能为空"));
            }
            if (mcp.getExposureMode() == McpToolExposureMode.SELECTED_ONLY
                    && (mcp.getMcpToolIds() == null || mcp.getMcpToolIds().isEmpty())) {
                return VerifyResult.invalid(new VerifyFail("mcps.mcpToolIds", "局部选择MCP工具时工具ID列表不能为空"));
            }
            if (containsNull(mcp.getMcpToolIds())) {
                return VerifyResult.invalid(new VerifyFail("mcps.mcpToolIds", "MCP工具ID列表不能包含空值"));
            }
        }
        return VerifyResult.valid();
    }

    private boolean containsNull(List<Long> ids) {
        return ids != null && ids.stream().anyMatch(Objects::isNull);
    }
}

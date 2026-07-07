package com.hxh.apboa.engine.tool;

import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.inputout.OutputConfig;
import com.hxh.apboa.node.base.request.ParamItem;
import com.hxh.apboa.node.start.Config;
import com.hxh.apboa.node.start.Param;
import com.hxh.apboa.node.start.StartNode;
import com.hxh.apboa.workflow.run.RunWorkflow;
import com.hxh.apboa.workflowbiz.core.WorkflowDefinitionCompiler;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunRequest;
import com.hxh.apboa.workflowbiz.service.WorkflowRunService;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 描述：流程工具
 *
 * @author huxuehao
 **/
public class WorkflowTool implements AgentTool {

    private static final Map<OutputConfig.VariableType, String> typeOptions = new HashMap<>();
    static {
        typeOptions.put(OutputConfig.VariableType.String, "string");
        typeOptions.put(OutputConfig.VariableType.Long, "number");
        typeOptions.put(OutputConfig.VariableType.Integer, "integer");
        typeOptions.put(OutputConfig.VariableType.Float, "number");
        typeOptions.put(OutputConfig.VariableType.Double, "number");
        typeOptions.put(OutputConfig.VariableType.Boolean, "boolean");
        typeOptions.put(OutputConfig.VariableType.Array, "object");
        typeOptions.put(OutputConfig.VariableType.Object, "object");
    }

    private final Workflow workflow;
    private final WorkflowRunService workflowRunService;

    private Config startConfig;

    public WorkflowTool(
            Workflow workflow,
            WorkflowVersion publishedVersion,
            WorkflowRunService workflowRunService,
            WorkflowDefinitionCompiler compiler) {
        this.workflow = workflow;
        this.workflowRunService = workflowRunService;
        RunWorkflow runWorkflow = compiler.compile(publishedVersion.getWorkflowId(), publishedVersion.getConfig());
        List<Node> list = runWorkflow.getNodes()
                .stream()
                .filter(node -> node.getType() == NodeType.START)
                .toList();
        if (!list.isEmpty()) {
            StartNode startNode = (StartNode)list.getFirst();
            startConfig = startNode.getConfig();
        }
    }

    @Override
    public String getName() {
        return workflow.getName();
    }

    @Override
    public String getDescription() {
        return workflow.getRemark();
    }

    @Override
    public Map<String, Object> getParameters() {
        List<Param> params = startConfig.getParams();
        if (params == null || params.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (Param param : params) {
            String name = param.getName();
            String description = param.getRemark();
            String defaultValue = param.getValue();

            properties.put(name, new HashMap<>(){{
                put("type", typeOptions.get(param.getType()));

                if (!FuncUtils.isEmpty(description)) {
                    put("description", description);
                } else {
                    put("description", name);
                }
                if (!FuncUtils.isEmpty(defaultValue)) {
                    put("defaultValue", defaultValue);
                }
            }});

            if ( param.getRequired()) {
                required.add(name);
            }
        }

        return new HashMap<>() {{
            put("type", "object");
            put("properties", properties);
            put("required", required);
        }};
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            try {
                WorkflowRunResult run = workflowRunService.run(workflow.getId(), getRunRequest(param));
                return ToolResultBlock.of(
                        param.getToolUseBlock().getId(),
                        param.getToolUseBlock().getName(),
                        TextBlock.builder().text(JsonUtils.toJsonStr(run.getOutput())).build()
                );
            } catch (Exception e) {
                return ToolResultBlock.of(
                        param.getToolUseBlock().getId(),
                        param.getToolUseBlock().getName(),
                        TextBlock.builder().text(e.getMessage()).build()
                );
            }
        });
    }

    /**
     * 获取工具执行参数（Map 形式，按参数名取值）
     *
     * @param toolCallParam 工具调用参数
     * @return 工具执行参数 WorkflowRunRequest
     */
    private WorkflowRunRequest getRunRequest(ToolCallParam toolCallParam) {
        WorkflowRunRequest request = new WorkflowRunRequest();
        ArrayList<ParamItem> paramItems = new ArrayList<>();

        Map<String, Object> inputs = toolCallParam.getInput();
        List<Param> params = startConfig.getParams();
        params.forEach(param -> {
            String name = param.getName();
            Object value = inputs.get(name);
            if (value != null) {
                value = param.getValue();
            }

            paramItems.add(ParamItem.builder()
                    .name(name)
                    .value(String.valueOf(value))
                    .build());
        });

        request.setParams(paramItems);
        return request;
    }
}

package com.hxh.apboa.engine.tool;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.common.enums.NodeType;
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
            Object defaultValue = param.getValue();

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
        AgentContext agentContext = param.getContext().get(AgentContext.class);
        if (agentContext == null) {
            return Mono.just(ToolResultBlock.error("System error, AgentContext is null"));
        }

        // 获取 UserDetail。userInfo 为服务端认证身份，匿名会话（chatKey 免登、暂停恢复等无认证上下文）合法为 null，
        // 此时只带租户信息，用户字段留空
        AccountVO userInfo = agentContext.getUserInfo();
        UserDetail.UserDetailBuilder userDetailBuilder = UserDetail.builder()
                .tenantId(agentContext.getTenantId())
                .tenantCode(agentContext.getTenantCode());
        if (userInfo != null) {
            userDetailBuilder.id(userInfo.getId())
                    .name(userInfo.getNickname())
                    .username(userInfo.getUsername())
                    .email(userInfo.getEmail())
                    .tenantRole(userInfo.getTenantRole());
        }
        UserDetail userDetail = userDetailBuilder.build();

        String toolUseId = param.getToolUseBlock().getId();
        return Mono.fromCallable(() -> {
            // 把主 agent 上下文桥接到工作流执行线程：工具在独立调度线程上执行，主链的
            // AgentContext（ThreadLocal）不随线程走，不桥接则 WorkflowAgentNodeExecutor 里
            // 读不到外层上下文——MCP 断言无法带用户主体、成本流水也无法归属主 agent/渠道
            AgentContext outerOnThread = AgentContext.getIfExists().orElse(null);
            AgentContext.set(agentContext);
            ToolProgressBridge.bindCurrent(toolUseId);
            try {
                ToolProgressBridge.emit(toolUseId,
                        ToolProgressBridge.stage("WORKFLOW_STARTING", "工作流正在启动"));
                // 执行工作流
                WorkflowRunResult run = workflowRunService.run(
                        workflow.getId(), getRunRequest(param), userDetail,
                        new WorkflowNodeProgressListener(toolUseId));
                ToolProgressBridge.emit(toolUseId,
                        ToolProgressBridge.stage("WORKFLOW_FINISHING", "工作流正在收尾"));
                WorkflowProcessSnapshot process = WorkflowProcessSnapshot.from(
                        run, ToolProgressBridge.snapshot(toolUseId));
                return ToolResultBlock.builder()
                        .id(param.getToolUseBlock().getId())
                        .name(param.getToolUseBlock().getName())
                        .output(TextBlock.builder().text(JsonUtils.toJsonStr(run.getOutput())).build())
                        .metadata(Map.of(WorkflowProcessSnapshot.METADATA_KEY, process))
                        .build();
            } catch (Exception e) {
                String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                return ToolResultBlock.builder()
                        .id(param.getToolUseBlock().getId())
                        .name(param.getToolUseBlock().getName())
                        .output(TextBlock.builder().text("Error: 工作流执行失败：" + error).build())
                        .metadata(Map.of(
                                WorkflowProcessSnapshot.METADATA_KEY,
                                WorkflowProcessSnapshot.failed(error)))
                        .build();
            } finally {
                ToolProgressBridge.clearCurrent();
                if (outerOnThread != null) {
                    AgentContext.set(outerOnThread);
                } else {
                    AgentContext.clean();
                }
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
            // 模型实参优先，未传时才回退 Start 节点配置的默认值
            if (value == null) {
                value = param.getValue();
            }

            paramItems.add(ParamItem.builder()
                    .name(name)
                    .value(value)
                    .build());
        });

        request.setParams(paramItems);
        return request;
    }
}

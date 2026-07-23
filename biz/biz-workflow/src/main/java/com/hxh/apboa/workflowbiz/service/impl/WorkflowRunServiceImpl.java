package com.hxh.apboa.workflowbiz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowNodeExecution;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.common.enums.NodeRunStatus;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.common.enums.WorkflowRunStatus;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.context.NodeExecutionListener;
import com.hxh.apboa.node.base.request.ParamItem;
import com.hxh.apboa.node.base.request.RequestParams;
import com.hxh.apboa.workflow.run.RunWorkflow;
import com.hxh.apboa.workflow.run.cache.RunWorkflowCache;
import com.hxh.apboa.workflowbiz.core.WorkflowDefinitionCompiler;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunRequest;
import com.hxh.apboa.workflowbiz.mapper.WorkflowMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowNodeExecutionMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowRunMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowVersionMapper;
import com.hxh.apboa.workflowbiz.service.WorkflowRunService;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl extends ServiceImpl<WorkflowRunMapper, WorkflowRun> implements WorkflowRunService {
    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeExecutionMapper nodeExecutionMapper;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowDefinitionCompiler compiler;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRunResult debugRun(Long workflowId, WorkflowRunRequest request) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("workflow not found");
        }
        return doRun(workflow, request,  UserUtils.getUserDetail(), false, NodeExecutionListener.noop());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRunResult run(Long workflowId, WorkflowRunRequest request, UserDetail userDetail) {
        return run(workflowId, request, userDetail, NodeExecutionListener.noop());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRunResult run(Long workflowId, WorkflowRunRequest request, UserDetail userDetail,
                                 NodeExecutionListener executionListener) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("workflow not found");
        }
        return doRun(workflow, request, userDetail, true, executionListener);
    }

    @Override
    public List<WorkflowNodeExecution> nodeExecutions(Long runId) {
        return nodeExecutionMapper.selectList(new LambdaQueryWrapper<WorkflowNodeExecution>()
                .eq(WorkflowNodeExecution::getWorkflowRunId, String.valueOf(runId))
                .orderByAsc(WorkflowNodeExecution::getStartTime));
    }

    private WorkflowRunResult doRun(Workflow workflow, WorkflowRunRequest request, UserDetail userDetail,
                                    boolean publishedOnly, NodeExecutionListener executionListener) {
        WorkflowVersion publishedVersion = publishedOnly ? latestPublishedVersion(workflow.getId()) : null;
        Object config = publishedVersion == null ? workflow.getConfig() : publishedVersion.getConfig();
        if (config == null) {
            throw new RuntimeException("workflow config is empty");
        }
        RunWorkflow runWorkflow = publishedOnly
                ? RunWorkflowCache.get(String.valueOf(workflow.getId()), publishedVersion.getVersion())
                : null;
        if (runWorkflow == null) {
            runWorkflow = compiler.compile(String.valueOf(workflow.getId()), config);
            if (publishedOnly) {
                RunWorkflowCache.set(publishedVersion.getVersion(), runWorkflow);
            }
        }

        WorkflowRun run = new WorkflowRun();
        run.setRouteId(workflow.getRouteId());
        run.setWorkflowId(String.valueOf(workflow.getId()));
        run.setVersion(publishedVersion == null ? workflow.getVersion() : publishedVersion.getVersion());
        run.setConfig(config);
        run.setInputs(request);
        run.setStatus(WorkflowRunStatus.RUNNING);
        run.setStartTime(System.currentTimeMillis());
        save(run);

        NodeContext context = new NodeContext(String.valueOf(run.getId()));
        context.setExecutionListener(executionListener);
        context.setRequestParams(toRequestParams(request));
        if (request != null && request.getVariables() != null) {
            request.getVariables().forEach(context.getVariables()::storeVariable);
        }
        // 工作流名下传（智能体节点成本流水的归属快照）：run 行在本事务内未提交，
        // 节点执行期按 instanceId 反查 workflow_run 读不到，只能经变量上下文带入
        context.getVariables().storeVariable("workflowId", String.valueOf(workflow.getId()));
        context.getVariables().storeVariable("workflowName", workflow.getName());

        if (userDetail != null) {
            context.getVariables().storeVariable("tenantId", userDetail.getTenantId());
            context.getVariables().storeVariable("tenantCode", userDetail.getTenantCode());
            context.getVariables().storeVariable("userId", userDetail.getId());
            context.getVariables().storeVariable("userName", userDetail.getUsername());
        } else {
            throw new RuntimeException("user detail is empty");
        }

        Object output = null;
        String error = null;
        try {
            output = runWorkflow.execute(context);
        } catch (Exception e) {
            error = e.getMessage();
        }

        List<WorkflowNodeExecution> executions = persistNodeExecutions(workflow, run, context);
        boolean nodeFailed = executions.stream().anyMatch(x -> x.getStatus() == NodeRunStatus.FAIL);
        run.setOutputs(toJsonSafeOutputs(output));
        run.setError(error);
        run.setEndTime(System.currentTimeMillis());
        run.setStatus(error == null && !nodeFailed ? WorkflowRunStatus.SUCCESS : WorkflowRunStatus.FAIL);
        updateById(run);

        WorkflowRunResult result = new WorkflowRunResult();
        result.setRun(run);
        result.setOutput(output);
        result.setNodeExecutions(executions);
        return result;
    }

    private WorkflowVersion latestPublishedVersion(Long workflowId) {
        WorkflowVersion version = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, String.valueOf(workflowId))
                .orderByDesc(WorkflowVersion::getId)
                .last("limit 1"));
        if (version == null) {
            throw new RuntimeException("workflow has no published version");
        }
        return version;
    }

    private RequestParams toRequestParams(WorkflowRunRequest request) {
        List<ParamItem> params = request == null || request.getParams() == null ? List.of() : request.getParams();
        return RequestParams.builder().params(params).build();
    }

    private List<WorkflowNodeExecution> persistNodeExecutions(Workflow workflow, WorkflowRun run, NodeContext context) {
        List<WorkflowNodeExecution> executions = new ArrayList<>();
        for (NodeOutput output : context.getExecutionTrace()) {
            WorkflowNodeExecution execution = new WorkflowNodeExecution();
            execution.setRouteId(workflow.getRouteId());
            execution.setWorkflowId(String.valueOf(workflow.getId()));
            execution.setWorkflowRunId(String.valueOf(run.getId()));
            execution.setNodeId(output.getNodeId());
            execution.setNodeTitle(output.getNodeName());
            execution.setNodeType(NodeType.valueOf(output.getNodeType().name()));
            execution.setInputs(toJson(output.getExecutionContext().get("inputs")));
            execution.setProcessData(toJson(output.getExecutionContext()));
            execution.setOutputs(toJson(output.getAllOutput()));
            execution.setStatus(toStatus(output.getStatus()));
            execution.setError(output.getErrorMessage() == null ? toJson(output.getVerifyErrors()) : output.getErrorMessage());
            execution.setStartTime(toMillis(output.getStartTime()));
            execution.setEndTime(toMillis(output.getEndTime()));
            nodeExecutionMapper.insert(execution);
            executions.add(execution);
        }
        return executions;
    }

    private NodeRunStatus toStatus(NodeOutput.ExecutionStatus status) {
        if (status == NodeOutput.ExecutionStatus.SUCCESS) {
            return NodeRunStatus.SUCCESS;
        }
        if (status == NodeOutput.ExecutionStatus.RUNNING) {
            return NodeRunStatus.RUNNING;
        }
        return NodeRunStatus.FAIL;
    }

    private Long toMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    /**
     * workflow_run.outputs 是 JSON 列，而 END 节点文本模板（如 STRING 格式化器）会产出裸字符串；
     * JsonUtils.toJsonStr 对 String 原样透传不做 JSON 编码，直接落库会因非法 JSON 报
     * Data truncation 并回滚整个 run。字符串统一包装为 JSON 文本节点后再落库，
     * 其余类型经 Jackson 序列化天然是合法 JSON。
     */
    private Object toJsonSafeOutputs(Object output) {
        return output instanceof String s ? TextNode.valueOf(s) : output;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}

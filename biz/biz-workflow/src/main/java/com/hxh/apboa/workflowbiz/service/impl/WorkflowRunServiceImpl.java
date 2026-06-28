package com.hxh.apboa.workflowbiz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowNodeExecution;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.common.enums.NodeRunStatus;
import com.hxh.apboa.common.enums.WorkflowRunStatus;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeContext;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        return doRun(workflow, request, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowRunResult run(Long workflowId, WorkflowRunRequest request) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("workflow not found");
        }
        return doRun(workflow, request, true);
    }

    @Override
    public List<WorkflowNodeExecution> nodeExecutions(Long runId) {
        return nodeExecutionMapper.selectList(new LambdaQueryWrapper<WorkflowNodeExecution>()
                .eq(WorkflowNodeExecution::getWorkflowRunId, String.valueOf(runId))
                .orderByAsc(WorkflowNodeExecution::getStartTime));
    }

    private WorkflowRunResult doRun(Workflow workflow, WorkflowRunRequest request, boolean publishedOnly) {
        WorkflowVersion publishedVersion = publishedOnly ? latestPublishedVersion(workflow.getId()) : null;
        Object config = publishedVersion == null ? workflow.getConfig() : publishedVersion.getConfig();
        if (config == null) {
            throw new RuntimeException("workflow config is empty");
        }
        RunWorkflow runWorkflow = publishedOnly ? RunWorkflowCache.get(String.valueOf(workflow.getId())) : null;
        if (runWorkflow == null) {
            runWorkflow = compiler.compile(String.valueOf(workflow.getId()), config);
            if (publishedOnly) {
                RunWorkflowCache.set(runWorkflow);
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
        context.setRequestParams(toRequestParams(request));
        if (request != null && request.getVariables() != null) {
            request.getVariables().forEach(context.getVariables()::storeVariable);
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
        run.setOutputs(output);
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
        for (Map.Entry<String, NodeOutput> entry : context.getVariables().getNodeOutputs().entrySet()) {
            NodeOutput output = entry.getValue();
            WorkflowNodeExecution execution = new WorkflowNodeExecution();
            execution.setRouteId(workflow.getRouteId());
            execution.setWorkflowId(String.valueOf(workflow.getId()));
            execution.setWorkflowRunId(String.valueOf(run.getId()));
            execution.setNodeId(output.getNodeId());
            execution.setNodeTitle(output.getNodeName());
            execution.setNodeType(com.hxh.apboa.common.enums.NodeType.valueOf(output.getNodeType().name()));
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

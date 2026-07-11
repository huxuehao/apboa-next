package com.hxh.apboa.workflowbiz.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.entity.WorkflowNodeExecution;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunRequest;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;

import java.util.List;

public interface WorkflowRunService extends IService<WorkflowRun> {
    WorkflowRunResult debugRun(Long workflowId, WorkflowRunRequest request);

    WorkflowRunResult run(Long workflowId, WorkflowRunRequest request, UserDetail userDetail);

    List<WorkflowNodeExecution> nodeExecutions(Long runId);
}

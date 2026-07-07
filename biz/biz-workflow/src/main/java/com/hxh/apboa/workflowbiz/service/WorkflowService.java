package com.hxh.apboa.workflowbiz.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.workflowbiz.vo.WorkflowDetailVO;
import com.hxh.apboa.workflowbiz.vo.WorkflowValidationResult;

import java.util.List;

public interface WorkflowService extends IService<Workflow> {
    WorkflowDetailVO detail(Long id);

    boolean saveWorkflow(Workflow workflow);

    boolean updateWorkflow(Workflow workflow);

    boolean deleteWorkflow(Integer force, List<Long> ids);

    List<Object> usedWithAgent(List<Long> ids);

    Workflow copyWorkflow(Long id);

    boolean lockWorkflow(Long id, Integer locked);

    WorkflowValidationResult validateWorkflow(Long id);

    WorkflowVersion publish(Long id, String remark);

    List<WorkflowVersion> versions(Long id);

    WorkflowVersion latestPublishedVersion(Long workflowId);

    Workflow rollback(Long id, String version);

    boolean deleteVersion(Long id, String version);
}

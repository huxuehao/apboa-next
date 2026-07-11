package com.hxh.apboa.workflowbiz.service;

import com.hxh.apboa.workflowbiz.vo.WorkflowResourceRefs;

import java.util.List;

public interface WorkflowResourceBindingService {
    WorkflowResourceRefs scan(Object definition);

    void sync(String workflowId, Object definition);

    WorkflowResourceRefs getRefs(String workflowId);

    List<String> usedWorkflowNames(String resourceType, List<String> resourceIds);

    void removeWorkflow(String workflowId);

    void removeResourceRefs(String resourceType, List<String> resourceIds);
}

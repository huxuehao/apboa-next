package com.hxh.apboa.workflowbiz.vo;

import com.hxh.apboa.common.entity.Workflow;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowDetailVO {
    private Workflow workflow;
    private WorkflowResourceRefs resources;
}

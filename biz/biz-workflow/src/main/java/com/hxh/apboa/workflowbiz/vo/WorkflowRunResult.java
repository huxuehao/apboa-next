package com.hxh.apboa.workflowbiz.vo;

import com.hxh.apboa.common.entity.WorkflowNodeExecution;
import com.hxh.apboa.common.entity.WorkflowRun;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WorkflowRunResult {
    private WorkflowRun run;
    private Object output;
    private List<WorkflowNodeExecution> nodeExecutions;
}

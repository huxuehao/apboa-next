package com.hxh.apboa.workflowbiz.dto;

import com.hxh.apboa.common.enums.WorkflowRunStatus;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowRunQueryDTO extends PageParams {
    @QueryDefine(condition = QueryCondition.EQ)
    private String workflowId;

    @QueryDefine(condition = QueryCondition.EQ)
    private String routeId;

    @QueryDefine(condition = QueryCondition.EQ)
    private WorkflowRunStatus status;
}

package com.hxh.apboa.workflowbiz.dto;

import com.hxh.apboa.common.enums.workflow.WorkflowStatus;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkflowQueryDTO extends PageParams {
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;

    @QueryDefine(condition = QueryCondition.EQ)
    private String routeId;

    @QueryDefine(condition = QueryCondition.EQ)
    private WorkflowStatus status;

    @QueryDefine(condition = QueryCondition.EQ)
    private Boolean enabled;
}

package com.hxh.apboa.workflowbiz.dto;

import com.hxh.apboa.node.base.request.ParamItem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class WorkflowRunRequest {
    private List<ParamItem> params;
    private Map<String, Object> variables;
}

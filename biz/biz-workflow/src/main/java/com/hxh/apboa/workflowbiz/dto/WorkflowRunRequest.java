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
    /**
     * 触发渠道（SysConst.CHANNEL_*），成本流水归因用：定时任务置 SCHEDULED；
     * 前端 HTTP 入口不传（null），节点记账回落对话渠道/STANDALONE 判定
     */
    private String channel;
}

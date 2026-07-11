package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.NodeRunStatus;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：流程节点执行日志表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.WORKFLOW_NODE_EXECUTION)
public class WorkflowNodeExecution extends BaseTenantEntity {
    /**
     * 路由ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String routeId;

    /**
     * 工作流ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String workflowId;

    /**
     * 工作流运行ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String workflowRunId;

    /**
     * 运行节点ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String nodeId;
    /**
     * 运行节点标题
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String nodeTitle;
    /**
     * 运行节点类型
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private NodeType nodeType;
    /**
     * 运行节点输入参数
     */
    private String inputs;
    /**
     * 运行节点处理数据
     */
    private String processData;
    /**
     * 运行节点输出参数
     */
    private String outputs;
    /**
     * 运行节点状态
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private NodeRunStatus status;
    /**
     * 运行节点错误信息
     */
    private String error;
    /**
     * 运行节点开始时间
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long startTime;
    /**
     * 运行节点结束时间
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long endTime;
}

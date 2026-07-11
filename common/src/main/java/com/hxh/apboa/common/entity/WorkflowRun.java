package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.WorkflowRunStatus;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：工作流执行记录表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(value = TableConst.WORKFLOW_RUN, autoResultMap = true)
public class WorkflowRun extends BaseTenantEntity {
    @QueryDefine(condition = QueryCondition.EQ)
    private String routeId;

    /**
     * 工作流ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String workflowId;
    @QueryDefine(condition = QueryCondition.EQ)
    private String version;
    /**
     * 工作流配置
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object config;
    /**
     * 工作流类型
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private WorkflowRunStatus status;
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object inputs;
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object outputs;
    private String error;
    private Long startTime;
    private Long endTime;
}

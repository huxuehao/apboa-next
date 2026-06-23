package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：工作流版本表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.WORKFLOW_VERSION)
public class WorkflowVersion extends BaseTenantEntity {
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
     * 备注
     */
    private String remark;
    /**
     * 工作流配置
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object config;
    /**
     * 工作流版本
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String version;
}

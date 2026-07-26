package com.hxh.apboa.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关API与已发布工作流关联表（n个API可绑定同一个工作流）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.GATEWAY_API_WORKFLOW)
public class GatewayApiWorkflow extends BaseTenantEntity {
    /**
     * 网关API ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long apiId;
    /**
     * 工作流ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long workflowId;
}

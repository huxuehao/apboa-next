package com.hxh.apboa.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关客户端与API授权关系表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.GATEWAY_CLIENT_API)
public class GatewayClientApi extends BaseTenantEntity {
    /**
     * 客户端ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long clientId;
    /**
     * 网关API ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long apiId;
}

package com.hxh.apboa.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关Token颁发日志表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.GATEWAY_TOKEN_LOG)
public class GatewayTokenLog extends BaseTenantEntity {
    /**
     * 客户端编号
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String clientCode;
    /**
     * 访问IP
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String accessIp;
    /**
     * 颁发状态：1成功、0失败
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer status;
    /**
     * 错误信息
     */
    private String error;
}

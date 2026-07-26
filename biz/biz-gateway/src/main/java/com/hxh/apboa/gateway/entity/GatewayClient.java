package com.hxh.apboa.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 描述：网关客户端表（Token体系的授权主体）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.GATEWAY_CLIENT)
public class GatewayClient extends BaseTenantEntity {
    /**
     * 客户端编号（获取Token的凭证标识）
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String code;
    /**
     * 客户端名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 消费方（客户端归属方）
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String consumer;
    /**
     * 客户端过期时间（null表示永不过期）
     */
    private LocalDateTime expireAt;
    /**
     * Token签名密钥（HMAC256）
     */
    private String tokenSecret;
    /**
     * Token有效期（毫秒）
     */
    private Long tokenTtl;
    /**
     * 在线状态：1在线、0下线
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer online;
}

package com.hxh.apboa.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.gateway.option.GatewayAppConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关应用表（一个应用对应一个监听端口）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(value = TableConst.GATEWAY_APP, autoResultMap = true)
public class GatewayApp extends BaseTenantEntity {
    /**
     * 应用名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 描述
     */
    private String remark;
    /**
     * 服务协议
     */
    private String protocol;
    /**
     * 监听端口
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer port;
    /**
     * 应用配置
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object config;
    /**
     * 在线状态：1在线、0下线
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer online;

    /**
     * 将JSON配置转换为强类型配置对象
     */
    public GatewayAppConfig obtainConfig() {
        if (config == null) {
            return new GatewayAppConfig();
        }
        GatewayAppConfig appConfig = JsonUtils.parse(JsonUtils.toJsonStr(config), GatewayAppConfig.class);
        return appConfig == null ? new GatewayAppConfig() : appConfig;
    }
}

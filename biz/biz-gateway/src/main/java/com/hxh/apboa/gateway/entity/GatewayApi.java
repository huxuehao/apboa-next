package com.hxh.apboa.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.gateway.enums.GatewayHttpMethod;
import com.hxh.apboa.gateway.option.GatewayApiConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关API表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(value = TableConst.GATEWAY_API, autoResultMap = true)
public class GatewayApi extends BaseTenantEntity {
    /**
     * 所属应用ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long appId;
    /**
     * 自维护分类
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String category;
    /**
     * API名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 描述
     */
    private String remark;
    /**
     * 请求方法
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private GatewayHttpMethod method;
    /**
     * 路由路径，支持 :param 占位符
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String path;
    /**
     * API配置
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
    public GatewayApiConfig obtainConfig() {
        if (config == null) {
            return new GatewayApiConfig();
        }
        GatewayApiConfig apiConfig = JsonUtils.parse(JsonUtils.toJsonStr(config), GatewayApiConfig.class);
        return apiConfig == null ? new GatewayApiConfig() : apiConfig;
    }
}

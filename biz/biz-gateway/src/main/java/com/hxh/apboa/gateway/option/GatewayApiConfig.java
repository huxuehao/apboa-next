package com.hxh.apboa.gateway.option;

import com.hxh.apboa.gateway.enums.GatewayAuthType;
import com.hxh.apboa.gateway.enums.GatewayLimitType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：网关API配置（存储于 gateway_api.config JSON 字段）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class GatewayApiConfig {
    /**
     * 鉴权类型
     */
    private GatewayAuthType authType = GatewayAuthType.TOKEN;
    /**
     * 携带Token的请求头名称
     */
    private String authHeaderName = "X-Apboa-Token";

    /**
     * 访问限制类型
     */
    private GatewayLimitType limitType = GatewayLimitType.NONE;
    /**
     * 时间窗内API总访问次数上限（小于等于0表示不限制）
     */
    private Integer routeTimes;
    /**
     * 时间窗内单IP访问次数上限（小于等于0表示不限制）
     */
    private Integer ipTimes;

    /**
     * 允许的Content-Type（为空表示不限制）
     */
    private List<String> contentTypes;
    /**
     * 请求参数定义（PATH/QUERY/HEADER/BODY字段级）
     */
    private List<GatewayApiParam> params;
    /**
     * 整体请求体映射到的工作流参数名（为空表示不做整体映射）
     */
    private String wholeBodyParam;
}

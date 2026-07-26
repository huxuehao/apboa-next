package com.hxh.apboa.gatewayrunner.core;

/**
 * 描述：网关请求上下文键常量
 *
 * @author huxuehao
 **/
public class GatewayContextKeys {
    /** 访问日志实体 */
    public static final String ACCESS_LOG = "apboa.gateway.access.log";
    /** 转换后的工作流入参列表 */
    public static final String PARAM_ITEMS = "apboa.gateway.param.items";

    /** 响应头 Server 标识 */
    public static final String SERVER_NAME = "Apboa-Gateway";
}

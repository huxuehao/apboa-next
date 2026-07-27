package com.hxh.apboa.gateway.enums;

/**
 * 描述：网关API鉴权类型
 *
 * @author huxuehao
 **/
public enum GatewayAuthType {
    /** 平台鉴权：解析Authorization请求头，支持平台登录token与已注册的SK */
    TOKEN,
    /** 免鉴权访问 */
    NONE
}

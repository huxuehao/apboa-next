package com.hxh.apboa.gateway.enums;

/**
 * 描述：网关API参数位置
 *
 * @author huxuehao
 **/
public enum GatewayParamPosition {
    /** 路径参数（path 中的 :name 占位符） */
    PATH,
    /** 查询参数 */
    QUERY,
    /** 请求头参数 */
    HEADER,
    /** 请求体JSON顶层字段 */
    BODY
}

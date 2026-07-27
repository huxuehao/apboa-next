package com.hxh.apboa.gateway.enums;

/**
 * 描述：网关API参数类型
 *
 * @author huxuehao
 **/
public enum GatewayParamType {
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    /** JSON对象（仅BODY位置支持） */
    OBJECT,
    /** JSON数组（仅BODY位置支持） */
    ARRAY
}

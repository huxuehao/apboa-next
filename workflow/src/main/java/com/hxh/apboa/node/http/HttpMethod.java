package com.hxh.apboa.node.http;

/**
 * 描述：
 *
 * @author huxuehao
 **/
public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS;

    /**
     * 该请求方法是否允许携带请求体（GET/HEAD 不允许）
     */
    public boolean permitsBody() {
        return this != GET && this != HEAD;
    }

    /**
     * 该请求方法是否必须携带请求体（OkHttp 对 POST/PUT/PATCH 强制要求）
     */
    public boolean requiresBody() {
        return this == POST || this == PUT || this == PATCH;
    }
}

package com.hxh.apboa.gateway.handler;

import io.vertx.ext.web.RoutingContext;

/**
 * 描述：请求来源IP解析工具
 * 优先取代理透传头，供访问日志与白名单校验共用
 *
 * @author huxuehao
 **/
public class RequestIpResolver {

    private RequestIpResolver() {
    }

    /**
     * 解析访问IP，优先取代理透传头
     */
    public static String resolve(RoutingContext ctx) {
        String forwarded = ctx.request().getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = ctx.request().getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return ctx.request().remoteAddress() == null ? null : ctx.request().remoteAddress().host();
    }
}

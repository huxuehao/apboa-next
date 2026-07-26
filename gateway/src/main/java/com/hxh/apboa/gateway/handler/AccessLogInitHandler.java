package com.hxh.apboa.gateway.handler;

import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.core.GatewayContextKeys;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * 描述：访问日志起始处理器
 * 作为API处理链的第一个环节，构造访问日志并放入请求上下文
 *
 * @author huxuehao
 **/
public class AccessLogInitHandler implements Handler<RoutingContext> {
    private final GatewayApiOption api;

    public AccessLogInitHandler(GatewayApiOption api) {
        this.api = api;
    }

    @Override
    public void handle(RoutingContext ctx) {
        GatewayAccessLog accessLog = new GatewayAccessLog();
        accessLog.setTenantId(api.getTenantId());
        accessLog.setAppId(api.getAppId());
        accessLog.setApiId(api.getId());
        accessLog.setMethod(ctx.request().method().name());
        accessLog.setPath(ctx.request().path());
        accessLog.setAccessIp(resolveIp(ctx));
        accessLog.setStartTime(System.currentTimeMillis());
        ctx.put(GatewayContextKeys.ACCESS_LOG, accessLog);
        ctx.next();
    }

    /**
     * 解析访问IP，优先取代理透传头
     */
    private String resolveIp(RoutingContext ctx) {
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

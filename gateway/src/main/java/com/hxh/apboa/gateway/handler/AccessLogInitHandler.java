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
        accessLog.setAccessIp(RequestIpResolver.resolve(ctx));
        accessLog.setStartTime(System.currentTimeMillis());
        ctx.put(GatewayContextKeys.ACCESS_LOG, accessLog);
        ctx.next();
    }
}

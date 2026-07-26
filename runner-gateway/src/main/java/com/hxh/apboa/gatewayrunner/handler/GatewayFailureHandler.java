package com.hxh.apboa.gatewayrunner.handler;

import com.hxh.apboa.gatewayrunner.log.GatewayLogWriter;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 描述：API处理链异常兜底处理器
 * 捕获处理链中未处理的异常，统一输出500响应并记录失败日志
 *
 * @author huxuehao
 **/
@Slf4j
public class GatewayFailureHandler implements Handler<RoutingContext> {
    private final GatewayLogWriter logWriter;

    public GatewayFailureHandler(GatewayLogWriter logWriter) {
        this.logWriter = logWriter;
    }

    @Override
    public void handle(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        // 请求体超限由BodyHandler以413状态码触发
        int statusCode = ctx.statusCode() > 0 ? ctx.statusCode() : 500;
        String message = statusCode == 413
                ? "请求体大小超过应用限制"
                : (failure == null || failure.getMessage() == null ? "网关内部错误" : failure.getMessage());
        if (failure != null) {
            log.error("网关请求处理异常: {}", failure.getMessage(), failure);
        }
        GatewayResponses.fail(ctx, statusCode, message, logWriter);
    }
}

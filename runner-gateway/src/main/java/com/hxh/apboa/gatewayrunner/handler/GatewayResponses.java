package com.hxh.apboa.gatewayrunner.handler;

import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import com.hxh.apboa.gatewayrunner.core.GatewayContextKeys;
import com.hxh.apboa.gatewayrunner.log.GatewayLogWriter;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * 描述：网关统一响应工具
 * 错误响应统一JSON结构，并补全访问日志后推入异步写入队列
 *
 * @author huxuehao
 **/
public class GatewayResponses {
    private static final int MAX_BODY_LOG_LENGTH = 60000;

    /**
     * 结束请求并输出错误响应，同时记录失败日志
     */
    public static void fail(RoutingContext ctx, int statusCode, String message, GatewayLogWriter logWriter) {
        if (!ctx.response().ended()) {
            ctx.response()
                    .setStatusCode(statusCode)
                    .putHeader("Server", GatewayContextKeys.SERVER_NAME)
                    .putHeader("Content-Type", "application/json;charset=UTF-8")
                    .end(new JsonObject().put("code", statusCode).put("message", message).toString());
        }
        GatewayAccessLog accessLog = ctx.get(GatewayContextKeys.ACCESS_LOG);
        if (accessLog != null && logWriter != null) {
            accessLog.setStatus(0);
            accessLog.setHttpStatus(statusCode);
            accessLog.setError(truncate(message));
            accessLog.setEndTime(System.currentTimeMillis());
            logWriter.pushAccessLog(accessLog);
        }
    }

    /**
     * 截断超长文本，避免日志字段溢出
     */
    public static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_BODY_LOG_LENGTH ? text : text.substring(0, MAX_BODY_LOG_LENGTH);
    }
}

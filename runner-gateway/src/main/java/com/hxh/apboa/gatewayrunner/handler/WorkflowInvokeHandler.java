package com.hxh.apboa.gatewayrunner.handler;

import com.hxh.apboa.common.enums.WorkflowRunStatus;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gatewayrunner.core.GatewayContextKeys;
import com.hxh.apboa.gatewayrunner.core.WorkflowInvoker;
import com.hxh.apboa.gatewayrunner.log.GatewayLogWriter;
import com.hxh.apboa.node.base.request.ParamItem;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;

/**
 * 描述：工作流执行处理器
 * 处理链的终点：将转换后的入参交给工作流执行，并把执行输出序列化为HTTP响应
 *
 * @author huxuehao
 **/
public class WorkflowInvokeHandler implements Handler<RoutingContext> {
    private final GatewayApiOption api;
    private final WorkflowInvoker invoker;
    private final GatewayLogWriter logWriter;

    public WorkflowInvokeHandler(GatewayApiOption api, WorkflowInvoker invoker, GatewayLogWriter logWriter) {
        this.api = api;
        this.invoker = invoker;
        this.logWriter = logWriter;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (api.getWorkflowId() == null) {
            GatewayResponses.fail(ctx, 500, "API未绑定工作流", logWriter);
            return;
        }
        List<ParamItem> params = ctx.get(GatewayContextKeys.PARAM_ITEMS);

        invoker.invoke(api, params).whenComplete((result, error) ->
                // 回到事件循环线程写响应
                ctx.vertx().runOnContext(v -> respond(ctx, result, error)));
    }

    /**
     * 根据执行结果输出响应并记录日志
     */
    private void respond(RoutingContext ctx, WorkflowRunResult result, Throwable error) {
        if (error != null) {
            GatewayResponses.fail(ctx, 500, rootMessage(error), logWriter);
            return;
        }

        GatewayAccessLog accessLog = ctx.get(GatewayContextKeys.ACCESS_LOG);
        if (accessLog != null && result.getRun() != null) {
            accessLog.setWorkflowRunId(result.getRun().getId());
        }

        // 工作流运行失败
        if (result.getRun() != null && result.getRun().getStatus() == WorkflowRunStatus.FAIL) {
            String message = result.getRun().getError() == null ? "工作流执行失败" : result.getRun().getError();
            GatewayResponses.fail(ctx, 500, message, logWriter);
            return;
        }

        String responseBody = serialize(ctx, result.getOutput());

        if (accessLog != null) {
            accessLog.setStatus(1);
            accessLog.setHttpStatus(ctx.response().getStatusCode());
            accessLog.setResponseBody(GatewayResponses.truncate(responseBody));
            accessLog.setEndTime(System.currentTimeMillis());
            logWriter.pushAccessLog(accessLog);
        }
    }

    /**
     * 按输出类型序列化响应
     *
     * @return 用于日志采集的响应体文本
     */
    private String serialize(RoutingContext ctx, Object output) {
        ctx.response().putHeader("Server", GatewayContextKeys.SERVER_NAME);

        if (output == null) {
            ctx.response().setStatusCode(204).end();
            return null;
        }
        if (output instanceof String text) {
            ctx.response().putHeader("Content-Type", "text/plain;charset=UTF-8").end(text);
            return text;
        }
        if (output instanceof byte[] bytes) {
            ctx.response().putHeader("Content-Type", "application/octet-stream").end(Buffer.buffer(bytes));
            return "[binary " + bytes.length + " bytes]";
        }
        if (output instanceof Map || output instanceof List
                || output instanceof Number || output instanceof Boolean) {
            String json = JsonUtils.toJsonStr(output);
            ctx.response().putHeader("Content-Type", "application/json;charset=UTF-8").end(json);
            return json;
        }
        String json = JsonUtils.toJsonStr(output);
        ctx.response().putHeader("Content-Type", "application/json;charset=UTF-8").end(json);
        return json;
    }

    /**
     * 提取最底层异常信息
     */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "工作流执行异常" : cause.getMessage();
    }
}

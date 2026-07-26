package com.hxh.apboa.gatewayrunner.handler;

import com.hxh.apboa.gateway.enums.GatewayParamPosition;
import com.hxh.apboa.gateway.enums.GatewayParamType;
import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.option.GatewayApiParam;
import com.hxh.apboa.gatewayrunner.core.GatewayContextKeys;
import com.hxh.apboa.gatewayrunner.log.GatewayLogWriter;
import com.hxh.apboa.node.base.request.ParamItem;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：请求参数转换处理器
 * 按API参数定义从PATH、QUERY、HEADER、BODY中提取参数值，完成必填校验、默认值填充与类型转换，
 * 最终转换为工作流开始节点的入参列表放入上下文
 *
 * @author huxuehao
 **/
public class ParamCheckHandler implements Handler<RoutingContext> {
    private final GatewayApiOption api;
    private final GatewayLogWriter logWriter;

    public ParamCheckHandler(GatewayApiOption api, GatewayLogWriter logWriter) {
        this.api = api;
        this.logWriter = logWriter;
    }

    @Override
    public void handle(RoutingContext ctx) {
        List<GatewayApiParam> paramDefs = api.getConfig().getParams() == null
                ? List.of() : api.getConfig().getParams();
        String wholeBodyParam = api.getConfig().getWholeBodyParam();

        // 按需解析JSON请求体
        Object parsedBody = null;
        boolean needBody = wholeBodyParam != null && !wholeBodyParam.isBlank()
                || paramDefs.stream().anyMatch(p -> p.getPosition() == GatewayParamPosition.BODY);
        if (needBody) {
            try {
                parsedBody = parseBody(ctx);
            } catch (Exception e) {
                GatewayResponses.fail(ctx, 400, "请求体不是合法的JSON", logWriter);
                return;
            }
        }

        List<ParamItem> paramItems = new ArrayList<>();
        JsonObject pathParams = new JsonObject();
        JsonObject queryParams = new JsonObject();
        JsonObject headerParams = new JsonObject();

        for (GatewayApiParam def : paramDefs) {
            Object rawValue = extractValue(ctx, def, parsedBody);

            // 必填校验与默认值填充
            if (isEmpty(rawValue)) {
                if (Boolean.TRUE.equals(def.getRequired())) {
                    GatewayResponses.fail(ctx, 400, "缺失必要的参数: " + def.getKey(), logWriter);
                    return;
                }
                if (def.getDefaultVal() == null || def.getDefaultVal().isBlank()) {
                    continue;
                }
                rawValue = def.getDefaultVal();
            }

            // 类型转换
            Object value;
            try {
                value = convert(rawValue, def.getType());
            } catch (Exception e) {
                GatewayResponses.fail(ctx, 400, "参数 " + def.getKey() + " 类型错误，期望 " + def.getType(), logWriter);
                return;
            }

            paramItems.add(buildParamItem(def.obtainWorkflowParam(), value));
            collectForLog(def, value, pathParams, queryParams, headerParams);
        }

        // 整体请求体映射
        if (wholeBodyParam != null && !wholeBodyParam.isBlank()) {
            paramItems.add(buildParamItem(wholeBodyParam, toJavaValue(parsedBody)));
        }

        // 补全访问日志的参数采集
        GatewayAccessLog accessLog = ctx.get(GatewayContextKeys.ACCESS_LOG);
        if (accessLog != null) {
            accessLog.setPathParams(pathParams.isEmpty() ? null : pathParams.toString());
            accessLog.setQueryParams(queryParams.isEmpty() ? null : queryParams.toString());
            accessLog.setHeaderParams(headerParams.isEmpty() ? null : headerParams.toString());
            if (needBody && ctx.body() != null && ctx.body().buffer() != null) {
                accessLog.setRequestBody(GatewayResponses.truncate(ctx.body().buffer().toString()));
            }
        }

        ctx.put(GatewayContextKeys.PARAM_ITEMS, paramItems);
        ctx.next();
    }

    /**
     * 解析JSON请求体，空请求体返回null
     */
    private Object parseBody(RoutingContext ctx) {
        if (ctx.body() == null || ctx.body().buffer() == null || ctx.body().buffer().length() == 0) {
            return null;
        }
        String text = ctx.body().buffer().toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("[")) {
            return new JsonArray(text);
        }
        return new JsonObject(text);
    }

    /**
     * 按参数位置提取原始值
     */
    private Object extractValue(RoutingContext ctx, GatewayApiParam def, Object parsedBody) {
        return switch (def.getPosition()) {
            case PATH -> ctx.pathParam(def.getKey());
            case QUERY -> ctx.request().getParam(def.getKey());
            case HEADER -> ctx.request().getHeader(def.getKey());
            case BODY -> parsedBody instanceof JsonObject body ? body.getValue(def.getKey()) : null;
        };
    }

    /**
     * 类型转换，将原始值转换为工作流入参需要的Java类型
     */
    private Object convert(Object rawValue, GatewayParamType type) {
        if (rawValue == null) {
            return null;
        }
        GatewayParamType targetType = type == null ? GatewayParamType.STRING : type;
        String text = rawValue instanceof String s ? s : String.valueOf(rawValue);
        return switch (targetType) {
            case STRING -> text;
            case INTEGER -> rawValue instanceof Number n ? n.intValue() : Integer.parseInt(text);
            case LONG -> rawValue instanceof Number n ? n.longValue() : Long.parseLong(text);
            case DOUBLE -> rawValue instanceof Number n ? n.doubleValue() : Double.parseDouble(text);
            case BOOLEAN -> rawValue instanceof Boolean b ? b : parseBoolean(text);
            case OBJECT -> toJavaValue(rawValue instanceof JsonObject ? rawValue : new JsonObject(text));
            case ARRAY -> toJavaValue(rawValue instanceof JsonArray ? rawValue : new JsonArray(text));
        };
    }

    /**
     * 严格布尔解析，非true/false一律视为类型错误
     */
    private boolean parseBoolean(String text) {
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("not a boolean: " + text);
    }

    /**
     * vertx JSON类型转换为普通Java集合，避免工作流侧感知vertx类型
     */
    private Object toJavaValue(Object value) {
        if (value instanceof JsonObject jsonObject) {
            return jsonObject.getMap();
        }
        if (value instanceof JsonArray jsonArray) {
            return jsonArray.getList();
        }
        return value;
    }

    private ParamItem buildParamItem(String name, Object value) {
        ParamItem item = new ParamItem();
        item.setName(name);
        item.setValue(value);
        return item;
    }

    /**
     * 参数值按位置归集，用于访问日志采集
     */
    private void collectForLog(GatewayApiParam def, Object value,
                               JsonObject pathParams, JsonObject queryParams, JsonObject headerParams) {
        switch (def.getPosition()) {
            case PATH -> pathParams.put(def.getKey(), value);
            case QUERY -> queryParams.put(def.getKey(), value);
            case HEADER -> headerParams.put(def.getKey(), value);
            default -> { }
        }
    }

    private boolean isEmpty(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }
}

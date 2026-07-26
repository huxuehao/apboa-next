package com.hxh.apboa.gatewayrunner.core;

import com.hxh.apboa.gateway.enums.GatewayHttpMethod;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.option.GatewayAppConfig;
import com.hxh.apboa.gateway.option.GatewayAppOption;
import com.hxh.apboa.gatewayrunner.handler.AccessLogInitHandler;
import com.hxh.apboa.gatewayrunner.handler.AuthHandler;
import com.hxh.apboa.gatewayrunner.handler.GatewayFailureHandler;
import com.hxh.apboa.gatewayrunner.handler.ParamCheckHandler;
import com.hxh.apboa.gatewayrunner.handler.RateLimitHandler;
import com.hxh.apboa.gatewayrunner.handler.WorkflowInvokeHandler;
import com.hxh.apboa.gatewayrunner.log.GatewayLogWriter;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：已部署的网关应用
 * 持有应用的HttpServer与Router，负责API路由的挂载与卸载
 *
 * @author huxuehao
 **/
public class DeployedApp {
    @Getter
    private final GatewayAppOption appOption;
    private final Router router;
    private final HttpServer server;
    private final ClientRegistry clientRegistry;
    private final WorkflowInvoker workflowInvoker;
    private final GatewayLogWriter logWriter;

    /** 已挂载的API路由：apiId -> Route */
    private final Map<Long, Route> apiRoutes = new ConcurrentHashMap<>();

    public DeployedApp(GatewayAppOption appOption, Router router, HttpServer server,
                       ClientRegistry clientRegistry, WorkflowInvoker workflowInvoker, GatewayLogWriter logWriter) {
        this.appOption = appOption;
        this.router = router;
        this.server = server;
        this.clientRegistry = clientRegistry;
        this.workflowInvoker = workflowInvoker;
        this.logWriter = logWriter;
    }

    /**
     * 初始化应用级路由（CORS与404兜底）
     */
    public void initAppRoutes() {
        GatewayAppConfig config = appOption.getConfig();
        if (Boolean.TRUE.equals(config.getCorsOpen())) {
            router.route().handler(buildCorsHandler(config));
        }
        // 404兜底路由，order置为最大保证最后匹配
        router.route().order(Integer.MAX_VALUE).handler(ctx ->
                ctx.response()
                        .setStatusCode(404)
                        .putHeader("Server", GatewayContextKeys.SERVER_NAME)
                        .putHeader("Content-Type", "application/json;charset=UTF-8")
                        .end(new JsonObject().put("code", 404).put("message", "请求路径不存在").toString()));
    }

    /**
     * 挂载API处理链：请求体读取、日志起始、鉴权、限流、参数转换、工作流执行、异常兜底
     */
    public void mountApi(GatewayApiOption api) {
        // 已存在旧路由时先卸载（更新场景）
        removeApi(api.getId());

        Route route = router.route(api.getPath());
        if (api.getMethod() != null && api.getMethod() != GatewayHttpMethod.ALL) {
            route.method(HttpMethod.valueOf(api.getMethod().name()));
        }
        if (api.getConfig().getContentTypes() != null) {
            api.getConfig().getContentTypes().forEach(route::consumes);
        }

        long bodyLimit = appOption.getConfig().getContentLength() == null || appOption.getConfig().getContentLength() <= 0
                ? -1 : appOption.getConfig().getContentLength();
        route.handler(BodyHandler.create().setBodyLimit(bodyLimit))
                .handler(new AccessLogInitHandler(api))
                .handler(new AuthHandler(api, clientRegistry, logWriter))
                .handler(new RateLimitHandler(api, logWriter))
                .handler(new ParamCheckHandler(api, logWriter))
                .handler(new WorkflowInvokeHandler(api, workflowInvoker, logWriter))
                .failureHandler(new GatewayFailureHandler(logWriter));

        apiRoutes.put(api.getId(), route);
    }

    /**
     * 卸载API路由
     */
    public boolean removeApi(Long apiId) {
        Route route = apiRoutes.remove(apiId);
        if (route != null) {
            route.disable().remove();
            return true;
        }
        return false;
    }

    /**
     * 判断API是否已挂载
     */
    public boolean containsApi(Long apiId) {
        return apiRoutes.containsKey(apiId);
    }

    /**
     * 已挂载的API ID集合
     */
    public Set<Long> apiIds() {
        return new HashSet<>(apiRoutes.keySet());
    }

    /**
     * 关闭HTTP服务
     */
    public void close() {
        apiRoutes.values().forEach(route -> route.disable().remove());
        apiRoutes.clear();
        server.close();
    }

    /**
     * 构建CORS处理器
     */
    private CorsHandler buildCorsHandler(GatewayAppConfig config) {
        String origin = config.getAllowedOrigin();
        CorsHandler corsHandler = CorsHandler.create()
                .addRelativeOrigin(origin == null || origin.isBlank() || "*".equals(origin) ? ".*" : origin);
        if (config.getAllowCredentials() != null) {
            corsHandler.allowCredentials(config.getAllowCredentials());
        }
        corsHandler.maxAgeSeconds(config.getMaxAgeSeconds() == null ? 60 : config.getMaxAgeSeconds());

        Set<HttpMethod> methods = new HashSet<>();
        List<String> allowedMethods = config.getAllowedMethods();
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            methods.add(HttpMethod.GET);
            methods.add(HttpMethod.POST);
            methods.add(HttpMethod.PUT);
            methods.add(HttpMethod.DELETE);
        } else {
            allowedMethods.forEach(method -> methods.add(HttpMethod.valueOf(method.toUpperCase())));
        }
        corsHandler.allowedMethods(methods);
        return corsHandler;
    }
}

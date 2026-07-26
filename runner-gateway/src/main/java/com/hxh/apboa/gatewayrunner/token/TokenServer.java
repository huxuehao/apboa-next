package com.hxh.apboa.gatewayrunner.token;

import com.hxh.apboa.gateway.entity.GatewayTokenLog;
import com.hxh.apboa.gateway.option.GatewayClientOption;
import com.hxh.apboa.gatewayrunner.config.GatewayRunnerProperties;
import com.hxh.apboa.gatewayrunner.core.ClientRegistry;
import com.hxh.apboa.gatewayrunner.core.GatewayContextKeys;
import com.hxh.apboa.gatewayrunner.log.GatewayLogWriter;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 描述：Token颁发服务
 * 在独立端口提供 GET /oauth/accessToken 接口，客户端凭 clientCode + clientSecret 换取JWT。
 * 与业务API端口隔离，不依赖平台登录体系
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenServer {
    private static final String TOKEN_PATH = "/oauth/accessToken";

    private final Vertx vertx;
    private final GatewayRunnerProperties properties;
    private final ClientRegistry clientRegistry;
    private final GatewayLogWriter logWriter;

    /**
     * 启动Token颁发服务
     */
    public void start() {
        int port = properties.getToken().getPort();
        Router router = Router.router(vertx);
        router.get(TOKEN_PATH).handler(this::issueToken);

        vertx.createHttpServer().requestHandler(router).listen(port)
                .onSuccess(server -> log.info("网关Token服务已启动，端口: {}，路径: GET {}?clientCode=xxx&clientSecret=xxx",
                        port, TOKEN_PATH))
                .onFailure(e -> log.error("网关Token服务启动失败: {}", e.getMessage(), e));
    }

    /**
     * 颁发Token
     */
    private void issueToken(RoutingContext ctx) {
        String clientCode = ctx.request().getParam("clientCode");
        String clientSecret = ctx.request().getParam("clientSecret");

        if (clientCode == null || clientCode.isBlank()) {
            respondError(ctx, 400, "缺少必要请求参数clientCode");
            return;
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            respondError(ctx, 400, "缺少必要请求参数clientSecret");
            recordLog(ctx, clientCode, 0, "缺少clientSecret");
            return;
        }

        GatewayClientOption client = clientRegistry.get(clientCode);
        if (client == null) {
            respondError(ctx, 401, "客户端不存在");
            recordLog(ctx, clientCode, 0, "客户端不存在");
            return;
        }
        if (!client.valid()) {
            respondError(ctx, 401, "客户端无效或已过期");
            recordLog(ctx, clientCode, 0, "客户端无效或已过期");
            return;
        }
        if (!client.getTokenSecret().equals(clientSecret)) {
            respondError(ctx, 401, "客户端密钥错误");
            recordLog(ctx, clientCode, 0, "客户端密钥错误");
            return;
        }

        long ttl = client.getTokenTtl() == null || client.getTokenTtl() <= 0
                ? 2 * 60 * 60 * 1000L : client.getTokenTtl();
        String token = GatewayTokenCodec.issue(clientCode, ttl, client.getTokenSecret());

        ctx.response()
                .putHeader("Server", GatewayContextKeys.SERVER_NAME)
                .putHeader("Content-Type", "application/json;charset=UTF-8")
                .end(new JsonObject()
                        .put("code", 200)
                        .put("token", token)
                        .put("expiresIn", ttl)
                        .toString());
        recordLog(ctx, clientCode, 1, null);
    }

    private void respondError(RoutingContext ctx, int statusCode, String message) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Server", GatewayContextKeys.SERVER_NAME)
                .putHeader("Content-Type", "application/json;charset=UTF-8")
                .end(new JsonObject().put("code", statusCode).put("message", message).toString());
    }

    /**
     * 记录Token颁发日志
     */
    private void recordLog(RoutingContext ctx, String clientCode, int status, String error) {
        GatewayClientOption client = clientRegistry.get(clientCode);
        GatewayTokenLog tokenLog = new GatewayTokenLog();
        tokenLog.setTenantId(client == null ? 0L : client.getTenantId());
        tokenLog.setClientCode(clientCode);
        tokenLog.setAccessIp(ctx.request().remoteAddress() == null ? null : ctx.request().remoteAddress().host());
        tokenLog.setStatus(status);
        tokenLog.setError(error);
        logWriter.pushTokenLog(tokenLog);
    }
}

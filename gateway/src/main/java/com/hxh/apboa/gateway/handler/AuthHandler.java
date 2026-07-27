package com.hxh.apboa.gateway.handler;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.gateway.auth.PlatformAuthVerifier;
import com.hxh.apboa.gateway.core.GatewayContextKeys;
import com.hxh.apboa.gateway.enums.GatewayAuthType;
import com.hxh.apboa.gateway.log.GatewayLogWriter;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * 描述：API鉴权处理器
 * 鉴权类型为TOKEN时解析请求头中的Authorization，复用平台统一凭证体系
 * （平台登录token与已注册的SK），并校验凭证归属租户与API归属租户一致；
 * 鉴权类型为NONE时直接放行
 *
 * @author huxuehao
 **/
public class AuthHandler implements Handler<RoutingContext> {
    private static final String AUTH_HEADER = "Authorization";

    private final GatewayApiOption api;
    private final PlatformAuthVerifier authVerifier;
    private final GatewayLogWriter logWriter;

    public AuthHandler(GatewayApiOption api, PlatformAuthVerifier authVerifier, GatewayLogWriter logWriter) {
        this.api = api;
        this.authVerifier = authVerifier;
        this.logWriter = logWriter;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (api.getConfig().getAuthType() == GatewayAuthType.NONE) {
            ctx.next();
            return;
        }

        String authHeader = ctx.request().getHeader(AUTH_HEADER);
        if (authHeader == null || authHeader.isBlank()) {
            GatewayResponses.fail(ctx, 401, "缺少 Authorization 请求头", logWriter);
            return;
        }

        // 校验涉及Redis访问，在虚拟线程中执行后回到事件循环继续处理链
        authVerifier.verifyAsync(authHeader).whenComplete((userDetail, error) ->
                ctx.vertx().runOnContext(v -> onVerified(ctx, userDetail, error)));
    }

    /**
     * 校验完成后的处理：失败返回401，成功校验租户归属并放行
     */
    private void onVerified(RoutingContext ctx, UserDetail userDetail, Throwable error) {
        if (error != null) {
            GatewayResponses.fail(ctx, 401, rootMessage(error), logWriter);
            return;
        }

        // 凭证归属租户必须与API归属租户一致，防止跨租户调用
        if (userDetail.getTenantId() == null || !userDetail.getTenantId().equals(api.getTenantId())) {
            GatewayResponses.fail(ctx, 401, "凭证无权访问该租户的API", logWriter);
            return;
        }

        // 认证用户放入上下文，工作流将以该用户身份执行
        ctx.put(GatewayContextKeys.AUTH_USER, userDetail);
        ctx.next();
    }

    /**
     * 提取最底层异常信息
     */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "凭证验证失败" : cause.getMessage();
    }
}

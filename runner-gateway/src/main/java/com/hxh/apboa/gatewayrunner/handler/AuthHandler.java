package com.hxh.apboa.gatewayrunner.handler;

import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.gateway.enums.GatewayAuthType;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.option.GatewayClientOption;
import com.hxh.apboa.gatewayrunner.core.ClientRegistry;
import com.hxh.apboa.gatewayrunner.log.GatewayLogWriter;
import com.hxh.apboa.gatewayrunner.token.GatewayTokenCodec;
import io.jsonwebtoken.ExpiredJwtException;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 描述：API鉴权处理器
 * 鉴权类型为TOKEN时校验请求头中的JWT：客户端存在且有效、拥有本API授权、
 * 使用客户端密钥验签且未过期；鉴权类型为NONE时直接放行
 *
 * @author huxuehao
 **/
public class AuthHandler implements Handler<RoutingContext> {
    private final GatewayApiOption api;
    private final ClientRegistry clientRegistry;
    private final GatewayLogWriter logWriter;

    public AuthHandler(GatewayApiOption api, ClientRegistry clientRegistry, GatewayLogWriter logWriter) {
        this.api = api;
        this.clientRegistry = clientRegistry;
        this.logWriter = logWriter;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (api.getConfig().getAuthType() == GatewayAuthType.NONE) {
            ctx.next();
            return;
        }

        String token = ctx.request().getHeader(api.getConfig().getAuthHeaderName());
        if (token == null || token.isBlank()) {
            GatewayResponses.fail(ctx, 401, "缺少访问凭证", logWriter);
            return;
        }

        // 先解出客户端编号，再用客户端密钥验签
        String clientCode = extractClientCode(token);
        if (clientCode == null) {
            GatewayResponses.fail(ctx, 401, "访问凭证格式有误", logWriter);
            return;
        }

        GatewayClientOption client = clientRegistry.get(clientCode);
        if (client == null) {
            GatewayResponses.fail(ctx, 401, "授权客户端不存在", logWriter);
            return;
        }
        if (!client.valid()) {
            GatewayResponses.fail(ctx, 401, "授权客户端无效或已过期", logWriter);
            return;
        }
        if (client.getApiIds() == null || !client.getApiIds().contains(api.getId())) {
            GatewayResponses.fail(ctx, 401, "访问凭证无权访问此API", logWriter);
            return;
        }

        try {
            GatewayTokenCodec.verify(token, client.getTokenSecret());
        } catch (ExpiredJwtException e) {
            GatewayResponses.fail(ctx, 401, "访问凭证已过期", logWriter);
            return;
        } catch (Exception e) {
            GatewayResponses.fail(ctx, 401, "访问凭证验证失败", logWriter);
            return;
        }

        ctx.next();
    }

    /**
     * 不验签解析JWT载荷中的客户端编号（subject）
     */
    private String extractClientCode(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return JsonUtils.parse(payload).path("sub").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}

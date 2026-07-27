package com.hxh.apboa.gateway.handler;

import com.hxh.apboa.gateway.core.GatewayContextKeys;
import com.hxh.apboa.gateway.option.GatewayAppConfig;
import com.hxh.apboa.gateway.option.GatewayAppWhitelistItem;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 描述：应用级访问白名单处理器
 * 作为应用路由链的第一道关卡：白名单为空放行，来源IP命中放行，否则拒绝
 *
 * @author huxuehao
 **/
public class WhitelistHandler implements Handler<RoutingContext> {
    /** 部署快照期的白名单IP集合，应用重部署时随配置整体刷新 */
    private final Set<String> whiteIps;

    public WhitelistHandler(GatewayAppConfig config) {
        this.whiteIps = config.getWhitelist() == null
                ? Set.of()
                : config.getWhitelist().stream()
                        .map(GatewayAppWhitelistItem::getIp)
                        .filter(ip -> ip != null && !ip.isBlank())
                        .map(ip -> normalizeIp(ip.trim()))
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (whiteIps.isEmpty()) {
            ctx.next();
            return;
        }
        // 白名单判定基于TCP层真实来源，透传头可伪造不可作为访问控制依据
        String ip = ctx.request().remoteAddress() == null ? null : ctx.request().remoteAddress().host();
        if (ip != null && whiteIps.contains(normalizeIp(ip))) {
            ctx.next();
            return;
        }
        ctx.response()
                .setStatusCode(403)
                .putHeader("Server", GatewayContextKeys.SERVER_NAME)
                .putHeader("Content-Type", "application/json;charset=UTF-8")
                .end(new JsonObject().put("code", 403).put("message", "当前IP无权访问").toString());
    }

    /**
     * IP归一化，消除IPv6缩写与完整展开等表示形式差异（如 ::1 与 0:0:0:0:0:0:0:1）
     * 入参均为已校验的IP字面量或连接层地址，不会触发DNS解析
     */
    private static String normalizeIp(String ip) {
        try {
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            return ip;
        }
    }
}

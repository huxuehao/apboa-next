package com.hxh.apboa.gateway.handler;

import com.hxh.apboa.gateway.enums.GatewayLimitType;
import com.hxh.apboa.gateway.option.GatewayApiConfig;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.log.GatewayLogWriter;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 描述：API访问限流处理器
 * 基于固定时间窗的内存计数限流，支持API总量限制与单IP限制同时生效；
 * 每个API路由持有独立的处理器实例，计数互不影响
 *
 * @author huxuehao
 **/
public class RateLimitHandler implements Handler<RoutingContext> {
    private final GatewayApiOption api;
    private final GatewayLogWriter logWriter;

    /** 当前时间窗起点 */
    private volatile long windowStart = System.currentTimeMillis();
    /** 时间窗内API总访问计数 */
    private final AtomicLong totalCount = new AtomicLong();
    /** 时间窗内单IP访问计数 */
    private final ConcurrentHashMap<String, AtomicLong> ipCounts = new ConcurrentHashMap<>();
    private final Object resetLock = new Object();

    public RateLimitHandler(GatewayApiOption api, GatewayLogWriter logWriter) {
        this.api = api;
        this.logWriter = logWriter;
    }

    @Override
    public void handle(RoutingContext ctx) {
        GatewayApiConfig config = api.getConfig();
        GatewayLimitType limitType = config.getLimitType();
        int routeTimes = config.getRouteTimes() == null ? 0 : config.getRouteTimes();
        int ipTimes = config.getIpTimes() == null ? 0 : config.getIpTimes();
        if (limitType == null || limitType == GatewayLimitType.NONE || (routeTimes <= 0 && ipTimes <= 0)) {
            ctx.next();
            return;
        }

        rollWindowIfExpired(limitType);

        // API总量限制
        if (routeTimes > 0 && totalCount.incrementAndGet() > routeTimes) {
            GatewayResponses.fail(ctx, 429, "请求次数超过访问限制，请稍后再试", logWriter);
            return;
        }

        // 单IP限制
        if (ipTimes > 0) {
            String ip = ctx.request().remoteAddress() == null ? "unknown" : ctx.request().remoteAddress().host();
            AtomicLong count = ipCounts.computeIfAbsent(ip, k -> new AtomicLong());
            if (count.incrementAndGet() > ipTimes) {
                GatewayResponses.fail(ctx, 429, "当前IP请求次数超过访问限制，请稍后再试", logWriter);
                return;
            }
        }

        ctx.next();
    }

    /**
     * 时间窗过期后滚动重置计数
     */
    private void rollWindowIfExpired(GatewayLimitType limitType) {
        long windowMillis = limitType.getSeconds() * 1000;
        long now = System.currentTimeMillis();
        if (now - windowStart >= windowMillis) {
            synchronized (resetLock) {
                if (now - windowStart >= windowMillis) {
                    windowStart = now;
                    totalCount.set(0);
                    ipCounts.clear();
                }
            }
        }
    }
}

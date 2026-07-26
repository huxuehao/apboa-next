package com.hxh.apboa.gatewayrunner.core;

import com.hxh.apboa.gateway.option.GatewayClientOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：客户端鉴权缓存
 * 以客户端编号为键的内存缓存，鉴权与Token颁发均走此缓存，避免请求打到数据库
 *
 * @author huxuehao
 **/
@Slf4j
@Component
public class ClientRegistry {
    private final Map<String, GatewayClientOption> cache = new ConcurrentHashMap<>();

    /**
     * 刷新客户端缓存
     */
    public void refresh(List<GatewayClientOption> clients) {
        if (clients == null) {
            return;
        }
        for (GatewayClientOption client : clients) {
            cache.put(client.getCode(), client);
            log.info("网关客户端 [{}] 缓存已刷新", client.getCode());
        }
    }

    /**
     * 移除客户端缓存
     */
    public void remove(List<String> codes) {
        if (codes == null) {
            return;
        }
        for (String code : codes) {
            cache.remove(code);
            log.info("网关客户端 [{}] 缓存已移除", code);
        }
    }

    /**
     * 按编号获取客户端
     */
    public GatewayClientOption get(String code) {
        return code == null ? null : cache.get(code);
    }
}

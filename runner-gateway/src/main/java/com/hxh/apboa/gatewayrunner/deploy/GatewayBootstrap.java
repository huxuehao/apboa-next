package com.hxh.apboa.gatewayrunner.deploy;

import com.hxh.apboa.gateway.service.GatewayDataService;
import com.hxh.apboa.gatewayrunner.core.ClientRegistry;
import com.hxh.apboa.gatewayrunner.core.GatewayLifecycleManager;
import com.hxh.apboa.gatewayrunner.token.TokenServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 描述：网关数据面启动引导
 * Spring容器就绪后启动Token服务、预热客户端鉴权缓存，
 * 并按数据库中的在线状态恢复所有应用与API路由
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayBootstrap implements SmartInitializingSingleton {
    private final TokenServer tokenServer;
    private final ClientRegistry clientRegistry;
    private final GatewayDataService dataService;
    private final GatewayLifecycleManager lifecycleManager;

    @Override
    public void afterSingletonsInstantiated() {
        // 启动Token颁发服务
        tokenServer.start();

        // 预热客户端鉴权缓存
        clientRegistry.refresh(dataService.loadAllClients());

        // 恢复在线应用与API
        dataService.loadOnlineApps().forEach(lifecycleManager::onlineApp);

        log.info("网关数据面启动完成");
    }
}

package com.hxh.apboa.gatewayrunner.cluster;

import com.hxh.apboa.common.cluster.core.ChannelSubscriber;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.gateway.cluster.GatewaySyncMessage;
import com.hxh.apboa.gateway.service.GatewayDataService;
import com.hxh.apboa.gatewayrunner.core.ClientRegistry;
import com.hxh.apboa.gatewayrunner.core.GatewayLifecycleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;

/**
 * 描述：网关集群同步订阅者
 * 订阅管理面广播的应用、API、客户端变更事件，按消息类型回查数据库最新配置后
 * 刷新本节点的路由状态与鉴权缓存，保证多节点部署下各节点状态一致
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewaySyncSubscriber implements ChannelSubscriber {
    private final GatewayLifecycleManager lifecycleManager;
    private final GatewayDataService dataService;
    private final ClientRegistry clientRegistry;

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.GATEWAY_SYNC_CHANNEL);
    }

    @Override
    public void onMessage(String channel, String message) {
        GatewaySyncMessage syncMessage;
        try {
            syncMessage = JsonUtils.parse(message, GatewaySyncMessage.class);
        } catch (Exception e) {
            log.error("网关同步消息解析失败: {}", message, e);
            return;
        }
        if (syncMessage == null || syncMessage.getType() == null) {
            return;
        }
        log.info("收到网关集群同步消息: {} {}", syncMessage.getType(), syncMessage.getIds());

        switch (syncMessage.getType()) {
            case APP_ONLINE -> dataService.loadApps(syncMessage.getIds()).forEach(lifecycleManager::onlineApp);
            case APP_OFFLINE -> syncMessage.getIds().forEach(lifecycleManager::offlineApp);
            case APP_RESET -> syncMessage.getIds().forEach(lifecycleManager::resetApp);
            case API_ONLINE -> lifecycleManager.onlineApis(dataService.loadApis(syncMessage.getIds()));
            case API_OFFLINE -> lifecycleManager.offlineApis(syncMessage.getIds());
            case API_RESET -> lifecycleManager.resetApis(syncMessage.getIds());
            case CLIENT_REFRESH -> clientRegistry.refresh(dataService.loadClients(syncMessage.getIds()));
            case CLIENT_REMOVE -> clientRegistry.remove(syncMessage.getCodes());
        }
    }
}

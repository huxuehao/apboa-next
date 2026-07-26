package com.hxh.apboa.gateway.cluster;

import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 描述：网关集群同步消息发布器
 * 管理面变更提交事务后向所有网关节点广播，触发各节点刷新本地路由状态
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class GatewaySyncPublisher {
    private final MessagePublisher messagePublisher;

    /**
     * 广播同步消息（事务提交后发送）
     */
    public void publish(GatewaySyncType type, List<Long> ids) {
        GatewaySyncMessage message = new GatewaySyncMessage(type, ids);
        messagePublisher.publishAfterCommit(RedisChannelTopic.GATEWAY_SYNC_CHANNEL, JsonUtils.toJsonStr(message));
    }
}

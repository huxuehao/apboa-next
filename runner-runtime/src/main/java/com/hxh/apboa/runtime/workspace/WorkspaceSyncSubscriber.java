package com.hxh.apboa.runtime.workspace;

import com.hxh.apboa.common.cluster.core.ChannelSubscriber;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 工作空间快照通知订阅者。
 *
 * @author huxuehao
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(WorkspaceSyncService.class)
public class WorkspaceSyncSubscriber implements ChannelSubscriber {

    private final WorkspaceSyncService syncService;

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.WORKSPACE_SYNC_CHANNEL);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!RedisChannelTopic.WORKSPACE_SYNC_CHANNEL.equals(channel)) {
            return;
        }
        try {
            syncService.acceptRemote(JsonUtils.parse(message, WorkspaceSyncService.WorkspaceSyncMessage.class));
        } catch (Exception e) {
            // Redis 消息异常不能影响订阅线程，下一次节点启动时会重新补偿。
        }
    }
}

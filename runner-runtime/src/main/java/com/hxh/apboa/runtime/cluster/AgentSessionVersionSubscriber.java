package com.hxh.apboa.runtime.cluster;

import com.hxh.apboa.common.cluster.core.ChannelSubscriber;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;

/**
 * Agent 会话缓存版本通知订阅者。
 *
 * @author huxuehao
 */
@Component
@RequiredArgsConstructor
public class AgentSessionVersionSubscriber implements ChannelSubscriber {

    private final AgentSessionVersionService versionService;

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.AGENT_SESSION_VERSION_CHANNEL);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (RedisChannelTopic.AGENT_SESSION_VERSION_CHANNEL.equals(channel)) {
            versionService.handleRemoteMessage(message);
        }
    }
}

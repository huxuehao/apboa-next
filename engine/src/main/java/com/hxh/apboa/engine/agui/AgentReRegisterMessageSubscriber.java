package com.hxh.apboa.engine.agui;

import com.hxh.apboa.common.cluster.core.ChannelSubscriber;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;

/**
 * 描述：edis 消息订阅者 - 仅处理 apboa:agent:cluster:reRegister 频道的跨节点消息
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentReRegisterMessageSubscriber implements ChannelSubscriber {

    private final AguiAgentConfiguration aguiAgentConfiguration;

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.AGENT_REREGISTER_CHANNEL);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (!channel.equals(RedisChannelTopic.AGENT_REREGISTER_CHANNEL)) {
            return;
        }

        aguiAgentConfiguration.reRegisterAgent(Long.parseLong(message));
    }
}

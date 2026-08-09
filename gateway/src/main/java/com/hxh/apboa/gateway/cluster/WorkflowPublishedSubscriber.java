package com.hxh.apboa.gateway.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.cluster.core.ChannelSubscriber;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.gateway.core.GatewayLifecycleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 描述：工作流发布事件订阅者
 * 工作流重新发布后失效本节点的编译缓存并重挂载绑定该工作流的在线API；
 * 工作流被删除后下线绑定的API
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowPublishedSubscriber implements ChannelSubscriber {
    private final GatewayLifecycleManager lifecycleManager;

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.WORKFLOW_PUBLISHED_CHANNEL);
    }

    @Override
    public void onMessage(String channel, String message) {
        try {
            JsonNode node = JsonUtils.parse(message);
            long workflowId = node.path("workflowId").asLong(0);
            String action = node.path("action").asText("");
            if (workflowId <= 0) {
                return;
            }
            log.info("收到工作流事件: workflowId={}, action={}", workflowId, action);
            if ("REMOVED".equals(action)) {
                lifecycleManager.onWorkflowRemoved(workflowId);
            } else {
                lifecycleManager.onWorkflowPublished(workflowId);
            }
        } catch (Exception e) {
            log.error("工作流事件处理失败: {}", message, e);
        }
    }
}

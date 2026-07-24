package com.hxh.apboa.websocket.handler.client;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.enums.WsMessageType;
import com.hxh.apboa.common.tts.TtsCtrlMessage;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.websocket.context.ApboaWebSocketSession;
import com.hxh.apboa.websocket.model.WsClientMessage;
import com.hxh.apboa.websocket.tts.TtsSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 描述：客户端订阅某 thread 语音播报的处理器。
 * 登记订阅关系并经控制频道通知 runtime 开合成会话（懒合成：无人订阅不烧算力）。
 *
 * @author huxuehao
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsSubscribeHandler implements ClientMessageHandler {

    private final TtsSubscriptionRegistry registry;
    private final MessagePublisher messagePublisher;

    @Override
    public WsMessageType messageType() {
        return WsMessageType.TTS_SUBSCRIBE;
    }

    @Override
    public void handle(ApboaWebSocketSession session, WsClientMessage msg) {
        Map<String, Object> content = asMap(msg);
        String threadId = content.get("threadId") == null ? null : String.valueOf(content.get("threadId"));
        Long agentId = content.get("agentId") == null ? null : Long.parseLong(String.valueOf(content.get("agentId")));
        if (threadId == null || threadId.isBlank() || agentId == null) {
            log.warn("TTS 订阅消息缺少 threadId/agentId，忽略");
            return;
        }

        registry.subscribe(threadId, session);

        UserDetail user = session.getUser();
        TtsCtrlMessage ctrl = TtsCtrlMessage.builder()
                .action(TtsCtrlMessage.ACTION_OPEN)
                .threadId(threadId)
                .agentId(agentId)
                .userId(user != null ? user.getId() : null)
                .tenantId(user != null ? user.getTenantId() : null)
                .build();
        messagePublisher.publish(RedisChannelTopic.TTS_CTRL_CHANNEL, JsonUtils.toJsonStr(ctrl));
        log.info("TTS 播报订阅：threadId={}, agentId={}", threadId, agentId);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(WsClientMessage msg) {
        if (msg != null && msg.getContent() instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @Override
    public ClientMessageHandler register() {
        return this;
    }
}

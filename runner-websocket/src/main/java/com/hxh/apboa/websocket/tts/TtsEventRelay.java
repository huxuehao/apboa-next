package com.hxh.apboa.websocket.tts;

import com.hxh.apboa.common.cluster.core.ChannelSubscriber;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.enums.WsMessageType;
import com.hxh.apboa.common.tts.TtsStreamEvent;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.websocket.config.ApboaWebSocketSessionManager;
import com.hxh.apboa.websocket.context.ApboaWebSocketSession;
import com.hxh.apboa.websocket.model.WsServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;

/**
 * TTS 流事件中转：订阅 Redis 事件频道（start/end/error），
 * 包装成 TTS_STREAM 类型的 WsServerMessage 转发给订阅该 thread 的前端。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsEventRelay implements ChannelSubscriber {

    private final TtsSubscriptionRegistry registry;

    @Override
    public Topic getTopic() {
        return new ChannelTopic(RedisChannelTopic.TTS_EVENT_CHANNEL);
    }

    @Override
    public void onMessage(String channel, String message) {
        TtsStreamEvent event = JsonUtils.parse(message, TtsStreamEvent.class);
        if (event == null || event.getThreadId() == null) {
            return;
        }
        WsServerMessage wsMessage = WsServerMessage.build(WsMessageType.TTS_STREAM.name(), event);
        for (ApboaWebSocketSession session : registry.subscribers(event.getThreadId())) {
            if (session.writeable()) {
                ApboaWebSocketSessionManager.sendBySession(session, wsMessage);
            }
        }
    }
}

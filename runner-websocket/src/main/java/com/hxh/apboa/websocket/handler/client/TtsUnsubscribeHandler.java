package com.hxh.apboa.websocket.handler.client;

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
 * 描述：客户端退订语音播报的处理器。
 * 该 thread 无人订阅时经控制频道通知 runtime 打断并释放合成会话。
 *
 * @author huxuehao
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsUnsubscribeHandler implements ClientMessageHandler {

    private final TtsSubscriptionRegistry registry;
    private final MessagePublisher messagePublisher;

    @Override
    public WsMessageType messageType() {
        return WsMessageType.TTS_UNSUBSCRIBE;
    }

    @Override
    public void handle(ApboaWebSocketSession session, WsClientMessage msg) {
        Map<String, Object> content = TtsSubscribeHandler.asMap(msg);
        String threadId = content.get("threadId") == null ? null : String.valueOf(content.get("threadId"));
        if (threadId == null || threadId.isBlank()) {
            return;
        }

        boolean drained = registry.unsubscribe(threadId, session);
        if (drained) {
            TtsCtrlMessage ctrl = TtsCtrlMessage.builder()
                    .action(TtsCtrlMessage.ACTION_CLOSE)
                    .threadId(threadId)
                    .build();
            messagePublisher.publish(RedisChannelTopic.TTS_CTRL_CHANNEL, JsonUtils.toJsonStr(ctrl));
        }
        log.info("TTS 播报退订：threadId={}, 会话释放={}", threadId, drained);
    }

    @Override
    public ClientMessageHandler register() {
        return this;
    }
}

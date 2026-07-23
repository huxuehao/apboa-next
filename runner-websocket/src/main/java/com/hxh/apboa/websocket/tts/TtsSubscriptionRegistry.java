package com.hxh.apboa.websocket.tts;

import com.hxh.apboa.websocket.context.ApboaWebSocketSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * TTS 播报订阅注册表：threadId → 订阅该流的 WS 会话集合。
 * 常态一个 thread 只有一个订阅端（发消息的那个页面），
 * 集合语义兼容同账号多端同听；session 断开由 Handler 统一清理。
 *
 * @author huxuehao
 */
@Component
public class TtsSubscriptionRegistry {

    private final Map<String, Set<ApboaWebSocketSession>> subscriptions = new ConcurrentHashMap<>();

    public void subscribe(String threadId, ApboaWebSocketSession session) {
        subscriptions.computeIfAbsent(threadId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    /**
     * 退订：返回该 thread 是否已无任何订阅端（是则通知 runtime 关会话）
     */
    public boolean unsubscribe(String threadId, ApboaWebSocketSession session) {
        Set<ApboaWebSocketSession> sessions = subscriptions.get(threadId);
        if (sessions == null) {
            return true;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            subscriptions.remove(threadId);
            return true;
        }
        return false;
    }

    public List<ApboaWebSocketSession> subscribers(String threadId) {
        Set<ApboaWebSocketSession> sessions = subscriptions.get(threadId);
        return sessions == null ? List.of() : List.copyOf(sessions);
    }

    /**
     * 连接断开清理：移除该 session 的全部订阅，返回因此变为无人订阅的 threadId 列表
     */
    public List<String> removeSession(ApboaWebSocketSession session) {
        return subscriptions.entrySet().stream()
                .filter(e -> e.getValue().remove(session) && e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .peek(subscriptions::remove)
                .toList();
    }
}

package com.hxh.apboa.runtime.cluster;

import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.RedisUtils;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 会话缓存版本管理服务。
 *
 * <p>Redis 版本号用于最终校准，Redis 频道仅用于加速其他节点淘汰本地缓存。
 *
 * @author huxuehao
 */
@Slf4j
@Service
public class AgentSessionVersionService {

    private static final String VERSION_KEY_PREFIX = "apboa:agent:session:version:";

    private final RedisUtils redisUtils;
    private final MessagePublisher messagePublisher;
    private final ThreadSessionManager sessionManager;
    private final Map<String, Long> localVersions = new ConcurrentHashMap<>();
    private final String nodeId = SysConst.CURRENT_NODE_ID;

    public AgentSessionVersionService(
            RedisUtils redisUtils,
            MessagePublisher messagePublisher,
            ThreadSessionManager sessionManager) {
        this.redisUtils = redisUtils;
        this.messagePublisher = messagePublisher;
        this.sessionManager = sessionManager;
    }

    /**
     * 在请求进入 Agent 前校准本地缓存版本。
     *
     * @param threadId 会话 ID
     */
    public void ensureFresh(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        long remoteVersion = readVersion(threadId);
        long localVersion = localVersions.getOrDefault(threadId, -1L);
        if (remoteVersion > localVersion && localVersion >= 0) {
            sessionManager.evict(threadId);
        }
        localVersions.put(threadId, remoteVersion);
    }

    /**
     * 标记本节点已经完成 Agent 状态持久化，并广播最新版本。
     *
     * @param threadId 会话 ID
     */
    public void markPersisted(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        long version = redisUtils.increment(versionKey(threadId));
        localVersions.put(threadId, version);
        String message = JsonUtils.toJsonStr(Map.of(
                "threadId", threadId,
                "version", version,
                "sourceNodeId", nodeId));
        messagePublisher.publish(RedisChannelTopic.AGENT_SESSION_VERSION_CHANNEL, message);
    }

    /**
     * 处理其他节点发来的版本通知。
     *
     * @param message 通知消息
     */
    public void handleRemoteMessage(String message) {
        try {
            AgentSessionVersionMessage payload = JsonUtils.parse(message, AgentSessionVersionMessage.class);
            if (payload == null || payload.threadId() == null || nodeId.equals(payload.sourceNodeId())) {
                return;
            }
            long localVersion = localVersions.getOrDefault(payload.threadId(), -1L);
            if (payload.version() > localVersion) {
                sessionManager.evict(payload.threadId());
                localVersions.put(payload.threadId(), payload.version());
            }
        } catch (Exception e) {
            log.warn("处理 Agent 会话版本通知失败: {}", e.getMessage());
        }
    }

    private long readVersion(String threadId) {
        String value = redisUtils.get(versionKey(threadId));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Agent 会话版本不是有效数字: threadId={}, value={}", threadId, value);
            return 0L;
        }
    }

    private String versionKey(String threadId) {
        return VERSION_KEY_PREFIX + threadId;
    }

    private record AgentSessionVersionMessage(String threadId, long version, String sourceNodeId) {
    }
}

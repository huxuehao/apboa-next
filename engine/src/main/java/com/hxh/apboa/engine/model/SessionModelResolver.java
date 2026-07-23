package com.hxh.apboa.engine.model;

import com.hxh.apboa.common.ApboaSpringContextHolder;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话级对话模型覆盖的唯一读取口（Redis，实时读，前台切换即写入）。
 *
 * <p>值语义：modelConfigId 字符串=覆盖为该候选模型 / 无 key=null（用 agent 默认模型）。
 * ChatModelFactory 构建模型时按此选定 modelConfigId（校验仍在候选集且可用，失效回落默认），
 * ReActAgentHelper 构建后把当次覆盖值记进 AgentMetadataStore，
 * AguiRequestProcessor 每次 run 对比检测变化触发 agent 重建（机制对齐 {@link ThinkingModeResolver}）。
 * 读取异常降级 null（跟随默认，不影响对话）。
 */
@Slf4j
public final class SessionModelResolver {

    private SessionModelResolver() {}

    /** 覆盖值的字符串形态（记录/比较用）：modelConfigId / "follow"（无覆盖） */
    public static final String FOLLOW = "follow";

    /** 读会话覆盖：modelConfigId=覆盖值，null=无覆盖（用默认模型） */
    public static Long resolveOverride(String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            return null;
        }
        try {
            RedisUtils redisUtils = ApboaSpringContextHolder.getBean(RedisUtils.class);
            String value = redisUtils.get(RedisChannelTopic.CHAT_MODEL_KEY_PREFIX + threadId);
            if (value == null || value.isEmpty()) {
                return null;
            }
            return Long.valueOf(value);
        } catch (Exception e) {
            log.warn("读取会话模型覆盖失败，按无覆盖处理: threadId={}, {}", threadId, e.getMessage());
            return null;
        }
    }

    /** 覆盖值 → 记录形态（AgentMetadataStore 存 String 便于比较） */
    public static String overrideKey(Long override) {
        return override == null ? FOLLOW : String.valueOf(override);
    }
}

package com.hxh.apboa.engine.model;

import com.hxh.apboa.common.ApboaSpringContextHolder;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话级思考模式覆盖的唯一读取口（Redis，实时读，前端切换即写入）。
 *
 * <p>值语义："1"=强制开 / "0"=强制关 / 无 key=null（默认开，由合成点兜底）。
 * ChatModelFactory 构建模型时按此合成 thinking（仅 DASH_SCOPE 生效），
 * ReActAgentHelper 构建后把当次覆盖值记进 AgentMetadataStore，
 * AguiRequestProcessor 每次 run 对比检测变化触发 agent 重建。
 * 读取异常降级 null（跟随默认，不影响对话）。
 *
 * @author huxuehao
 */
@Slf4j
public final class ThinkingModeResolver {

    private ThinkingModeResolver() {}

    /** 覆盖值的字符串形态（记录/比较用）："1" / "0" / "follow"（无覆盖） */
    public static final String FOLLOW = "follow";

    /** 读会话覆盖：true/false=覆盖值，null=无覆盖（默认开） */
    public static Boolean resolveOverride(String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            return null;
        }
        try {
            RedisUtils redisUtils = ApboaSpringContextHolder.getBean(RedisUtils.class);
            String value = redisUtils.get(RedisChannelTopic.CHAT_THINKING_KEY_PREFIX + threadId);
            if ("1".equals(value)) {
                return true;
            }
            if ("0".equals(value)) {
                return false;
            }
            return null;
        } catch (Exception e) {
            log.warn("读取会话思考模式失败，按无覆盖处理: threadId={}, {}", threadId, e.getMessage());
            return null;
        }
    }

    /** 覆盖值 → 记录形态（AgentMetadataStore 存 String 便于比较） */
    public static String overrideKey(Boolean override) {
        return override == null ? FOLLOW : (override ? "1" : "0");
    }
}

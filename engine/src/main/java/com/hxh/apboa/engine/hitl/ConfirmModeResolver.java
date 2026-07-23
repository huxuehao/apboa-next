package com.hxh.apboa.engine.hitl;

import com.hxh.apboa.common.ApboaSpringContextHolder;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.enums.ConfirmMode;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话 HITL 授权模式解析（{@link ConfirmMode} 三态的唯一读取口）。
 *
 * <p>实时读 Redis（会话级开关，前端切换即写入），主 agent 凭 threadId 直查，
 * 子智能体凭 agentId 反查 threadId（无则回退 subParentThreadId——主会话模式对
 * 子智能体内需确认工具同样生效，授权状态下行继承）。读取异常一律降级 MANUAL
 * （宁可多确认一次，不可静默放行/误拒）。静态形态供非 Spring 对象
 * （SubAgentTool、AguiAgentAdapter）直接调用，RedisUtils 经 SpringContextHolder 懒取。
 *
 * @author huxuehao
 */
@Slf4j
public final class ConfirmModeResolver {

    private ConfirmModeResolver() {}

    /** 按主会话 ID 解析授权模式 */
    public static ConfirmMode resolveByThreadId(String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            return ConfirmMode.MANUAL;
        }
        try {
            RedisUtils redisUtils = ApboaSpringContextHolder.getBean(RedisUtils.class);
            String value = redisUtils.get(RedisChannelTopic.CHAT_AUTO_APPROVE_KEY_PREFIX + threadId);
            return ConfirmMode.fromRedisValue(value);
        } catch (Exception e) {
            log.warn("读取授权模式失败，降级为逐步确认: threadId={}, {}", threadId, e.getMessage());
            return ConfirmMode.MANUAL;
        }
    }

    /** 按 agentId 解析：主 agent 走 threadId 登记，子智能体回退 subParentThreadId */
    public static ConfirmMode resolveByAgentId(String agentId) {
        if (agentId == null) {
            return ConfirmMode.MANUAL;
        }
        String threadId = AgentMetadataStore.get(agentId, "threadId");
        if (threadId == null) {
            threadId = AgentMetadataStore.get(agentId, "subParentThreadId");
        }
        return resolveByThreadId(threadId);
    }
}

package com.hxh.apboa.engine.hook.builtins;

import com.hxh.apboa.common.annotation.Scope;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.engine.hook.IAgentHook;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：工具确认Hook
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@Scope(ScopeType.GLOBAL)
@RequiredArgsConstructor
public class IConfirmationHook implements IAgentHook {
    // 需要确认的工具列表
    private static final List<String> NEED_CONFIRM_TOOLS = new ArrayList<>();

    private final RedisUtils redisUtils;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 监听 PostReasoningEvent
        if (event instanceof PostReasoningEvent postReasoning) {
            Msg reasoningMsg = postReasoning.getReasoningMessage();
            if (reasoningMsg == null) {
                return Mono.just(event);
            }

            // 获取工具调用列表
            List<ToolUseBlock> toolCalls = reasoningMsg.getContentBlocks(ToolUseBlock.class);
            if (toolCalls.isEmpty()) {
                return Mono.just(event);
            }

            // 收集需要确认的工具信息
            List<Map<String, Object>> toolsNeedConfirm = new ArrayList<>();
            for (ToolUseBlock tool : toolCalls) {
                if (NEED_CONFIRM_TOOLS.contains(tool.getName())) {
                    Map<String, Object> toolInfo = new HashMap<>();
                    toolInfo.put("id", tool.getId());
                    toolInfo.put("name", tool.getName());
                    toolInfo.put("input", convertInput(tool.getInput()));
                    toolInfo.put("dangerous", true);
                    toolsNeedConfirm.add(toolInfo);
                }
            }

            // 如果有需要确认的工具
            if (!toolsNeedConfirm.isEmpty()) {
                // 一键授权：stopAgent 前实时读 Redis 最新开关（会话级，前端切换即写入），
                // 开着则直接放行，避免白跑整套 暂停持久化→确认事件→前端确认→resume 循环
                if (isAutoApproved(event)) {
                    log.info("会话已开启一键授权，自动放行需确认工具: {}",
                            toolsNeedConfirm.stream().map(t -> t.get("name")).toList());
                    return Mono.just(event);
                }
                // 暂停 Agent 执行，等待用户确认
                postReasoning.stopAgent();
            }
        }

        return Mono.just(event);
    }

    /**
     * 查询当前会话是否开启「一键授权」。
     * threadId 经 AgentMetadataStore 反查（AguiRequestProcessor 已登记）；
     * Redis 读取异常降级为逐步确认——宁可多确认一次，不可静默放行。
     */
    private boolean isAutoApproved(HookEvent event) {
        try {
            if (!(event.getAgent() instanceof AgentBase agentBase)) {
                return false;
            }
            String threadId = AgentMetadataStore.get(agentBase.getAgentId(), "threadId");
            if (threadId == null) {
                return false;
            }
            return "1".equals(redisUtils.get(RedisChannelTopic.CHAT_AUTO_APPROVE_KEY_PREFIX + threadId));
        } catch (Exception e) {
            log.warn("读取一键授权开关失败，降级为逐步确认: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertInput(Object input) {
        if (input == null) {
            return Map.of();
        }
        if (input instanceof Map) {
            return (Map<String, Object>) input;
        }
        // 简单类型直接包装
        return Map.of("value", input.toString());
    }

    public static void setNeedConfirmTool(String toolName) {
        if (!NEED_CONFIRM_TOOLS.contains(toolName)) {
            NEED_CONFIRM_TOOLS.add(toolName);
        }
    }

    public static void removeNeedConfirmTool(String toolName) {
        NEED_CONFIRM_TOOLS.remove(toolName);
    }

    /**
     * 查询某工具是否登记为需确认。
     *
     * <p>HITL §6.2：AguiAgentAdapter 暂停时据此从本轮所有 pending 工具中精确过滤出需确认的，
     * 避免把同轮被 stopAgent 连累的普通/MCP 工具也算进 TOOL_CONFIRM_REQUIRED（修 §2.2「MCP 确认假象」）。
     */
    public static boolean isNeedConfirm(String toolName) {
        return NEED_CONFIRM_TOOLS.contains(toolName);
    }

    @Override
    public String getDescription() {
        return "人工确认钩子，配置后当需要确认的工具被调用时，用户决定是否执行";
    }
}

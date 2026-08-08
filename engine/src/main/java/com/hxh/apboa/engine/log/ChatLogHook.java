package com.hxh.apboa.engine.log;

import com.hxh.apboa.common.entity.ChatMessage;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.subagent.SubAgentTraceEvent;
import com.hxh.apboa.common.subagent.SubAgentTraceEventType;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：保存聊天记录hook
 *
 * @author huxuehao
 **/
public class ChatLogHook implements Hook {
    private final Map<String, Map<String, Object>> TOOL_CACHE_MAP = new ConcurrentHashMap<>();
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ActingChunkEvent actingChunkEvent) {
            Object value = actingChunkEvent.getChunk().getMetadata().get(SubAgentTraceEvent.METADATA_KEY);
            if (value instanceof SubAgentTraceEvent traceEvent) {
                persistSubAgentTrace(event, traceEvent);
                return Mono.just(event);
            }
        }

        // 子代理已通过其父级的 ActingChunkEvent 进行持久化。若将其自身的 hook 回调作为普通聊天消息持久化，
        // 会导致卡片交错与数据重复。
        if (isSubAgentChild(event)) {
            return Mono.just(event);
        }

        if (event instanceof PostReasoningEvent || event instanceof PostActingEvent || event instanceof ErrorEvent) {
            // 解析租户ID和线程ID
            Long tenantId = extractTenantId(event);
            String threadId = extractThreadId(event);
            if (tenantId == null || threadId == null) {
                return Mono.just(event);
            }

            // 处理错误事件
            if (event instanceof ErrorEvent errorEvent) {
                Throwable error = errorEvent.getError();
                ConcurrentLogProducer.pushLog(buildErrorMessage(
                        Long.valueOf(threadId),
                        error.getMessage(),
                        tenantId));

                return Mono.just(event);
            }

            // 解析工具调用是否活跃
            boolean toolProcessActive = extractToolProcessActive(event);

            // 处理 reasoning 后置事件
            if (event instanceof PostReasoningEvent postReasoningEvent) {
                Msg reasoningMessage = postReasoningEvent.getReasoningMessage();
                List<ContentBlock> content = reasoningMessage.getContent();
                if (content.isEmpty()) {
                    return Mono.just(event);
                }

                for (ContentBlock block : content) {
                    if (block instanceof TextBlock textBlock) {
                        // 正文保存
                        String longTextContent = getLongTextContent(textBlock);
                        if (longTextContent != null) {
                            ConcurrentLogProducer.pushLog(buildChatMessage(
                                    Long.valueOf(threadId),
                                    "assistant",
                                    longTextContent,
                                    tenantId));
                        }
                    } else if (block instanceof ThinkingBlock thinkingBlock) {
                        // 思考保存
                        String longThinkingContent = getLongThinkingContent(thinkingBlock);
                        if (longThinkingContent != null) {
                            ConcurrentLogProducer.pushLog(buildChatMessage(
                                    Long.valueOf(threadId),
                                    "thinking",
                                    longThinkingContent,
                                    tenantId));
                        }
                    } else if (block instanceof ToolUseBlock toolUseBlock && toolProcessActive) {
                        String longToolUseContent = getLongToolUseContent(toolUseBlock);
                        if (longToolUseContent != null) {
                            String toolCallId = toolUseBlock.getId();
                            if (toolCallId == null) {
                                toolCallId = UUID.randomUUID().toString();
                            }
                            // 工具调用入参暂保存
                            TOOL_CACHE_MAP.put(toolCallId, Map.of(
                                    "tool_name", toolUseBlock.getName(),
                                    "tool_args", longToolUseContent,
                                    "tool_start", System.currentTimeMillis()
                            ));
                        }

                    }
                }
            }
            // 处理 acting 后置事件
            else if (toolProcessActive) {
                PostActingEvent postActingEvent = (PostActingEvent) event;
                Msg reasoningMessage = postActingEvent.getToolResultMsg();
                List<ContentBlock> content = reasoningMessage.getContent();
                if (content.isEmpty()) {
                    return Mono.just(event);
                }

                // 工具结果保存
                for (ContentBlock block : content) {
                    if (block instanceof ToolResultBlock toolResult) {
                        String toolId = toolResult.getId();
                        if (toolResult.getMetadata().containsKey(SubAgentTraceEvent.FINAL_RESULT_METADATA_KEY)) {
                            Object invocationId = toolResult.getMetadata()
                                    .get(SubAgentTraceEvent.FINAL_RESULT_METADATA_KEY);
                            TOOL_CACHE_MAP.remove(toolId == null ? String.valueOf(invocationId) : toolId);
                            continue;
                        }
                        try {
                            Map<String, Object> toolCache = TOOL_CACHE_MAP.get(toolId);
                            if (toolCache == null) {
                                return Mono.just(event);
                            }
                            String toolRes = getLongToolResultContent(toolResult);
                            ConcurrentLogProducer.pushLog(buildChatMessage(
                                    Long.valueOf(threadId),
                                    "tool",
                                    buildToolContent(
                                            toolCache.get("tool_name").toString(),
                                            System.currentTimeMillis() - (Long) toolCache.get("tool_start"),
                                            toolCache.get("tool_args").toString(),
                                            toolRes),
                                    tenantId));
                        } finally {
                            TOOL_CACHE_MAP.remove(toolId);
                        }
                    }
                }
            }
        }

        return Mono.just(event);
    }

    private void persistSubAgentTrace(HookEvent event, SubAgentTraceEvent traceEvent) {
        Long tenantId = extractTenantId(event);
        String threadId = extractThreadId(event);
        if (tenantId == null || threadId == null) {
            return;
        }
        Long sessionId;
        try {
            sessionId = Long.valueOf(threadId);
        } catch (NumberFormatException ignored) {
            return;
        }

        BeanUtils.getBean(SubAgentTracePersistenceService.class).record(tenantId, sessionId, traceEvent);

        // 锚点参与正常的聊天分支路径；详细信息保留在专用的追踪表中，
        // 并在读取历史记录时批量加载。
        if (traceEvent.getEventType() == SubAgentTraceEventType.STARTED) {
            Map<String, Object> anchor = new HashMap<>();
            anchor.put("invocationId", traceEvent.getInvocationId());
            if (traceEvent.getAgent() != null) {
                anchor.put("agentTitle", traceEvent.getAgent().getTitle());
                anchor.put("agentCode", traceEvent.getAgent().getCode());
            }
            ConcurrentLogProducer.pushLog(buildChatMessage(
                    sessionId, "subagent", JsonUtils.toJsonStr(anchor), tenantId));
        }
    }

    private boolean isSubAgentChild(HookEvent event) {
        if (event.getAgent() instanceof AgentBase agentBase) {
            return AgentMetadataStore.get(agentBase.getAgentId(), "subagentInvocationId") != null;
        }
        return false;
    }

    private Long extractTenantId(HookEvent event) {
        if (event.getAgent() instanceof AgentBase agentBase) {
            return AgentMetadataStore.get(agentBase.getAgentId(), "tenantId");
        }
        return null;
    }
    private String extractThreadId(HookEvent event) {
        if (event.getAgent() instanceof AgentBase agentBase) {
            return AgentMetadataStore.get(agentBase.getAgentId(), "threadId");
        }
        return null;
    }
    private boolean extractToolProcessActive(HookEvent event) {
        if (event.getAgent() instanceof AgentBase agentBase) {
            Object o = AgentMetadataStore.get(agentBase.getAgentId(), "toolProcessActive");
            if (o instanceof Boolean b) return b;
        }
        return false;
    }

    private String getLongTextContent(TextBlock textBlock) {
        String text = textBlock.getText();
        if (text.isEmpty()) {
            return null;
        }
        return text;
    }

    // 获取思考内容
    private String getLongThinkingContent(ThinkingBlock thinkingBlock) {
        String thinking = thinkingBlock.getThinking();
        if (thinking.isEmpty()) {
            return null;
        }
        return thinking;
    }


    // 获取中文工具使用内容
    private String getLongToolUseContent(ToolUseBlock toolUseBlock) {
        String content = toolUseBlock.getContent();
        if (content.isEmpty()) {
            return null;
        }

        return content;
    }

    // 获取中文工具结果内容
    private String getLongToolResultContent(ToolResultBlock toolResultBlock) {
        ContentBlock first = toolResultBlock.getOutput().getFirst();
        return first.toString();
    }

    // 构建工具内容
    private String buildToolContent(String toolName, Long totalTimes, String args, String result) {
        Map<String, Object> toolContent = new HashMap<>() {{
            put("name", toolName);
            put("totalTimes", totalTimes);
            put("args", args);
            put("result", result);
        }};
        return JsonUtils.toJsonStr(toolContent);
    }

    // 构建聊天消息
    private ChatMessage buildChatMessage(Long sessionId, String role, String content, Long tenantId) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setRole(role);
        chatMessage.setContent(content);
        chatMessage.setTenantId(tenantId);

        return chatMessage;
    }

    // 构建错误消息
    private ChatMessage buildErrorMessage(Long sessionId, String content, Long tenantId) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setRole("error");
        chatMessage.setContent(content);
        chatMessage.setTenantId(tenantId);

        return chatMessage;
    }
}

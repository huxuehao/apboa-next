/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.agui.adapter;

import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.common.subagent.SubAgentTraceEvent;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.memory.ObservableAutoContextMemory;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agui.converter.AguiMessageConverter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.TokenCounterUtil;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonException;
import io.agentscope.core.util.JsonUtils;

import java.util.*;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Adapter that bridges AgentScope agents to the AG-UI protocol.
 *
 * <p>This adapter converts AG-UI protocol inputs to AgentScope messages,
 * invokes the agent, and converts the streaming events back to AG-UI events.
 *
 * <p><b>Event Mapping:</b>
 * <ul>
 *   <li>AgentScope REASONING events → AG-UI TEXT_MESSAGE_* events (for TextBlock)</li>
 *   <li>AgentScope REASONING events → AG-UI REASONING_* events (for ThinkingBlock, when enabled)</li>
 *   <li>AgentScope TOOL_RESULT events → AG-UI TOOL_CALL_END events</li>
 *   <li>ToolUseBlock content → AG-UI TOOL_CALL_START events</li>
 * </ul>
 *
 * <p><b>Reasoning Support:</b>
 * <ul>
 *   <li>ThinkingBlock content is converted to REASONING_* events according to AG-UI Reasoning draft</li>
 *   <li>Reasoning output is disabled by default (enableReasoning=false) for backward compatibility</li>
 *   <li>Set enableReasoning=true in AguiAdapterConfig to enable reasoning events</li>
 * </ul>
 */
public class AguiAgentAdapter {
    /** 本轮是否因 HITL 确认而暂停（REASONING_STOP_REQUESTED）。供 processor 决定是否无条件保存暂停态。 */
    @Getter
    private volatile boolean suspended = false;

    private final Agent agent;
    private final AguiAdapterConfig config;
    private final AguiMessageConverter messageConverter;

    /**
     * Creates a new AguiAgentAdapter.
     *
     * @param agent The agent to adapt
     * @param config The adapter configuration
     */
    public AguiAgentAdapter(Agent agent, AguiAdapterConfig config) {
        this.agent = Objects.requireNonNull(agent, "agent cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.messageConverter = new AguiMessageConverter();
    }

    /**
     * Run the agent with AG-UI protocol input.
     *
     * <p>This method converts the input messages, invokes the agent's streaming API,
     * and emits AG-UI protocol events.
     *
     * @param input The AG-UI run input
     * @return A Flux of AG-UI events
     */
    public Flux<AguiEvent> run(RunAgentInput input) {
        // Convert AG-UI messages to AgentScope messages
        List<Msg> msgs = messageConverter.toMsgList(input.getMessages());
        return runWithMessages(msgs, input.getThreadId(), input.getRunId());
    }

    /**
     * HITL resume：直接以 AgentScope 原生 Msg 列表继续执行（绕过 AG-UI 消息转换），
     * 复用同一套事件转换与 SSE 输出。
     *
     * <p>空列表 = 让 agent 继续执行 pending 工具（全允许）；
     * 传入含 ToolResultBlock 的 TOOL 角色消息 = 喂入工具结果（如「用户已拒绝执行」）后继续。
     */
    public Flux<AguiEvent> runWithMessages(List<Msg> msgs, String threadId, String runId) {
        // Create stream options - use incremental mode for true streaming
        StreamOptions options =
                StreamOptions.builder().eventTypes(EventType.ALL).incremental(true).build();

        // Track state for event conversion
        EventConversionState state = new EventConversionState(threadId, runId);
        state.compressionEventCount = currentCompressionEventCount();
        Sinks.Many<AguiEvent> lifecycleEvents = Sinks.many().unicast().onBackpressureBuffer();
        configureCompressionLifecycle(state, lifecycleEvents);

        Flux<AguiEvent> agentEvents = agent.stream(msgs, options)
                .concatMapIterable(event -> convertEvent(event, state))
                .doFinally(signal -> {
                    clearCompressionLifecycle();
                    lifecycleEvents.tryEmitComplete();
                });

        return Flux.concat(
                        // Emit RUN_STARTED and the initial context snapshot
                        Flux.concat(
                                Flux.just(new AguiEvent.RunStarted(threadId, runId)),
                                Flux.fromIterable(buildInitialContextUsageEvent(state))),
                        // Stream agent events and compression lifecycle events concurrently so
                        // STARTED can reach the client before the synchronous summary call ends.
                        Flux.merge(lifecycleEvents.asFlux(), agentEvents),
                        // Emit any pending end events and RUN_FINISHED
                        Flux.defer(() -> finishRun(state)))
                .onErrorResume(
                        error -> {
                            // On error, emit RawEvent with error info followed by RunFinished
                            String errorMessage =
                                    error.getMessage() != null
                                            ? error.getMessage()
                                            : error.getClass().getSimpleName();
                            return Flux.just(
                                    new AguiEvent.Raw(
                                            threadId, runId, Map.of("error", errorMessage)),
                                    new AguiEvent.RunFinished(threadId, runId));
                        });
    }

    /**
     * Convert an AgentScope event to AG-UI events.
     *
     * @param event The AgentScope event
     * @param state The conversion state
     * @return List of AG-UI events
     */
    private List<AguiEvent> convertEvent(Event event, EventConversionState state) {
        List<AguiEvent> events = new ArrayList<>();
        Msg msg = event.getMessage();
        EventType type = event.getType();

        // HITL：检测到「推理后暂停」即标记本轮挂起，供 AguiRequestProcessor 无条件保存暂停态
        if (msg != null && msg.getGenerateReason() == GenerateReason.REASONING_STOP_REQUESTED) {
            this.suspended = true;
        }

        if (type == EventType.REASONING) {
            // Handle reasoning events - convert to text messages and tool calls
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock textBlock) {
                    String text = textBlock.getText();
                    if (text != null && !text.isEmpty()) {
                        // Apboa 如果当前已有活跃的文本消息，复用其 messageId，避免产生多个 TEXT_MESSAGE_START
                        String messageId;
                        if (state.hasActiveTextMessage()) {
                            messageId = state.getCurrentTextMessageId();
                        } else {
                            messageId = msg.getId();
                        }

                        // Start message if not started
                        if (!state.hasStartedMessage(messageId)) {
                            events.add(
                                    new AguiEvent.ReasoningMessageEnd(
                                            state.threadId, state.runId, messageId));
                            events.add(
                                    new AguiEvent.TextMessageStart(
                                            state.threadId, state.runId, messageId, "assistant"));
                            state.startMessage(messageId);
                        }

                        if (!event.isLast()) {
                            // In incremental mode, text is already the delta
                            events.add(
                                    new AguiEvent.TextMessageContent(
                                            state.threadId, state.runId, messageId, text));
                        } else {
                            // End message if this is the last event
                            if (!state.hasEndedMessage(messageId)) {
                                events.add(
                                        new AguiEvent.TextMessageEnd(
                                                state.threadId, state.runId, messageId));
                                state.endMessage(messageId);
                            }
                        }
                    }
                } else if (block instanceof ThinkingBlock thinkingBlock) {
                    // Handle thinking blocks - convert to REASONING_* events (only if enabled)
                    // According to AG-UI Reasoning draft: https://docs.ag-ui.com/drafts/reasoning
                    if (config.isEnableReasoning()) {
                        String thinking = thinkingBlock.getThinking();
                        if (thinking != null && !thinking.isEmpty()) {
                            // Apboa 如果当前已有活跃的文本消息，复用其 messageId，避免产生多个 TEXT_MESSAGE_START
                            String messageId;
                            if (state.hasActiveTextMessage()) {
                                messageId = state.getCurrentTextMessageId();
                            } else {
                                messageId = msg.getId();
                            }

                            // Start reasoning message if not started
                            if (!state.hasStartedReasoningMessage(messageId)) {
                                events.add(
                                        new AguiEvent.ReasoningMessageStart(
                                                state.threadId,
                                                state.runId,
                                                messageId,
                                                "assistant"));
                                state.startReasoningMessage(messageId);
                            }

                            if (!event.isLast()) {
                                // In incremental mode, thinking is already the delta
                                events.add(
                                        new AguiEvent.ReasoningMessageContent(
                                                state.threadId, state.runId, messageId, thinking));
                            } else {
                                // End reasoning message if this is the last event
                                events.add(
                                        new AguiEvent.ReasoningMessageEnd(
                                                state.threadId, state.runId, messageId));
                                state.endReasoningMessage(messageId);
                            }
                        }
                    }
                    // If reasoning is disabled, ThinkingBlock content is ignored (backward
                    // compatibility)
                } else if (block instanceof ToolUseBlock toolUse) {
                    // End any active text message before starting tool call
                    if (state.hasActiveTextMessage()) {
                        String activeMessageId = state.getCurrentTextMessageId();
                        events.add(
                                new AguiEvent.TextMessageEnd(
                                        state.threadId, state.runId, activeMessageId));
                        events.add(
                                new AguiEvent.ReasoningMessageEnd(
                                        state.threadId, state.runId, activeMessageId));
                        state.endMessage(activeMessageId);
                    }

                    // Emit tool call start
                    String toolCallId = toolUse.getId();
                    if (toolCallId == null) {
                        toolCallId = UUID.randomUUID().toString();
                    }

                    if (!state.hasStartedToolCall(toolCallId)) {
                        events.add(
                                new AguiEvent.ToolCallStart(
                                        state.threadId,
                                        state.runId,
                                        toolCallId,
                                        toolUse.getName()));
                        state.startToolCall(toolCallId);
                    }

                    // Emit tool call args if enabled
                    if (config.isEmitToolCallArgs() && !event.isLast()) {
                        String args = toolUse.getContent();
                        if (args != null && !args.isEmpty()) {
                            events.add(
                                    new AguiEvent.ToolCallArgs(
                                            state.threadId, state.runId, toolCallId, args));
                        }
                    }
                }
            }
        } else if (type == EventType.TOOL_RESULT) {
            boolean subAgentTraceEvent = false;
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof ToolResultBlock toolResult
                        && toolResult.getMetadata().containsKey(SubAgentTraceEvent.METADATA_KEY)) {
                    Object trace = toolResult.getMetadata().get(SubAgentTraceEvent.METADATA_KEY);
                    events.add(new AguiEvent.Custom(
                            state.threadId,
                            state.runId,
                            SubAgentTraceEvent.CUSTOM_EVENT_NAME,
                            trace));
                    subAgentTraceEvent = true;
                }
            }

            if (subAgentTraceEvent) {
                // Intermediate sub-agent chunks are a separate custom protocol. They must never
                // enter the parent agent's text/tool conversion state.
                return events;
            }

            if (event.isLast()) {
                // Handle tool results
                for (ContentBlock block : msg.getContent()) {
                    if (block instanceof ToolResultBlock toolResult) {
                        if (toolResult.getMetadata().containsKey(SubAgentTraceEvent.FINAL_RESULT_METADATA_KEY)) {
                            // The dedicated sub-agent card replaces the generic parent tool card.
                            continue;
                        }
                        String toolCallId = toolResult.getId();
                        String result = extractToolResultText(toolResult);

                        boolean hasStarted = state.hasStartedToolCall(toolCallId);
                        if (!hasStarted) {
                            events.add(
                                    new AguiEvent.ToolCallStart(
                                            state.threadId, state.runId, toolCallId, "unknown"));
                            state.startToolCall(toolCallId);
                        }

                        // Ensure ToolCallEnd is emitted to close arguments phase
                        events.add(new AguiEvent.ToolCallEnd(state.threadId, state.runId, toolCallId));

                        events.add(
                                new AguiEvent.ToolCallResult(
                                        state.threadId,
                                        state.runId,
                                        toolCallId,
                                        result,
                                        "tool",
                                        msg.getId()));
                        state.endToolCall(toolCallId);
                    }
                }
            }
        } else if (type == EventType.AGENT_RESULT) {
            // pending 精确为 need_confirm 工具——过滤掉同轮被 stopAgent 连累的普通/MCP 工具。
            // 前端据此逐工具渲染「允许/禁止」，决策齐了调 /agui/resume。
            if (msg != null
                    && msg.getGenerateReason() == GenerateReason.REASONING_STOP_REQUESTED) {
                List<Map<String, Object>> pending = new ArrayList<>();
                for (ToolUseBlock toolUse : msg.getContentBlocks(ToolUseBlock.class)) {
                    if (IConfirmationHook.isNeedConfirm(toolUse.getName())) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("toolUseId", toolUse.getId());
                        item.put("name", toolUse.getName());
                        item.put("input", toolUse.getInput());
                        pending.add(item);
                    }
                }
                if (!pending.isEmpty()) {
                    events.add(
                            new AguiEvent.Custom(
                                    state.threadId,
                                    state.runId,
                                    "TOOL_CONFIRM_REQUIRED",
                                    Map.of("pending", pending)));
                }
            }
        }

        // 在一轮思考、工具执行或正文输出完成后推送上下文使用量。
        if (event.isLast()
                && (type == EventType.REASONING
                        || type == EventType.TOOL_RESULT
                        || type == EventType.AGENT_RESULT)) {
            events.addAll(buildContextUsageEvents(state, type));
        }

        return events;
    }

    /** 注册压缩开始监听器，使压缩模型调用前即可推送 SSE。 */
    private void configureCompressionLifecycle(
            EventConversionState state, Sinks.Many<AguiEvent> lifecycleEvents) {
        if (!(agent instanceof ReActAgent reActAgent)
                || !(reActAgent.getMemory() instanceof ObservableAutoContextMemory memory)) {
            return;
        }
        memory.setCompressionListener(started -> {
            state.compressionStarted = true;
            lifecycleEvents.tryEmitNext(new AguiEvent.Custom(
                    state.threadId,
                    state.runId,
                    "CONTEXT_COMPRESSION",
                    Map.of(
                            "status", "STARTED",
                            "usedTokens", started.usedTokens(),
                            "totalTokens", started.totalTokens(),
                            "phase", "COMPRESSION")));
        });
    }

    /** 清理监听器，避免一次运行的 SSE sink 被后续运行继续引用。 */
    private void clearCompressionLifecycle() {
        if (agent instanceof ReActAgent reActAgent
                && reActAgent.getMemory() instanceof ObservableAutoContextMemory memory) {
            memory.setCompressionListener(null);
        }
    }

    /** 获取当前记忆中的压缩事件数量，用于区分本轮新增压缩。 */
    private int currentCompressionEventCount() {
        if (agent instanceof ReActAgent reActAgent
                && reActAgent.getMemory() instanceof AutoContextMemory memory) {
            return memory.getCompressionEvents().size();
        }
        return 0;
    }

    /** 构建本轮运行开始时的上下文快照。 */
    private List<AguiEvent> buildInitialContextUsageEvent(EventConversionState state) {
        if (!(agent instanceof ReActAgent reActAgent)
                || !(reActAgent.getMemory() instanceof AutoContextMemory memory)) {
            return List.of();
        }
        long totalTokens = resolveContextLimit();
        int usedTokens = TokenCounterUtil.calculateToken(memory.getMessages());
        return List.of(new AguiEvent.Custom(
                state.threadId,
                state.runId,
                "CONTEXT_USAGE",
                Map.of(
                        "usedTokens", usedTokens,
                        "totalTokens", totalTokens,
                        "ratio", totalTokens > 0 ? Math.min(1D, usedTokens / (double) totalTokens) : 0D,
                        "phase", "RUN",
                        "estimated", true,
                        "compressed", memory.getCompressionEvents().size() > 0,
                        "timestamp", System.currentTimeMillis())));
    }

    /** 构建上下文使用量事件，并在检测到压缩后补发压缩完成事件。 */
    private List<AguiEvent> buildContextUsageEvents(EventConversionState state, EventType type) {
        if (!(agent instanceof ReActAgent reActAgent)
                || !(reActAgent.getMemory() instanceof AutoContextMemory memory)) {
            return List.of();
        }

        long totalTokens = resolveContextLimit();
        int usedTokens = TokenCounterUtil.calculateToken(memory.getMessages());
        int compressionEvents = memory.getCompressionEvents().size();
        List<AguiEvent> events = new ArrayList<>();

        if (compressionEvents > state.compressionEventCount) {
            // ObservableAutoContextMemory 会在压缩模型调用前推送 STARTED；非包装记忆保留兜底。
            if (!state.compressionStarted) {
                events.add(new AguiEvent.Custom(
                        state.threadId,
                        state.runId,
                        "CONTEXT_COMPRESSION",
                        Map.of(
                                "status", "STARTED",
                                "usedTokens", usedTokens,
                                "totalTokens", totalTokens,
                                "phase", "COMPRESSION")));
            }
            events.add(new AguiEvent.Custom(
                    state.threadId,
                    state.runId,
                    "CONTEXT_COMPRESSION",
                    Map.of(
                            "status", "FINISHED",
                            "usedTokens", usedTokens,
                            "totalTokens", totalTokens,
                            "phase", "COMPRESSION",
                            "compressed", true)));
            state.compressionEventCount = compressionEvents;
            state.compressionStarted = false;
        }

        String phase = switch (type) {
            case REASONING -> "REASONING";
            case TOOL_RESULT -> "TOOL";
            case AGENT_RESULT -> "ANSWER";
            default -> "UNKNOWN";
        };
        events.add(new AguiEvent.Custom(
                state.threadId,
                state.runId,
                "CONTEXT_USAGE",
                Map.of(
                        "usedTokens", usedTokens,
                        "totalTokens", totalTokens,
                        "ratio", totalTokens > 0 ? Math.min(1D, usedTokens / (double) totalTokens) : 0D,
                        "phase", phase,
                        "estimated", true,
                        "compressed", compressionEvents > 0,
                        "timestamp", System.currentTimeMillis())));
        return events;
    }

    /** 从当前 Agent 配置中读取上下文上限，读取失败时使用 AgentScope 默认值。 */
    private long resolveContextLimit() {
        return AgentContext.getIfExists()
                .map(AgentContext::getAgentDefinition)
                .map(definition -> definition.getMemoryCompressionConfig())
                .map(config -> com.hxh.apboa.common.util.JsonUtils.getLongValue(
                        config, "maxToken", 131072L))
                .orElse(131072L);
    }

    /**
     * Finish the run by emitting any pending end events and RUN_FINISHED.
     *
     * @param state The conversion state
     * @return Flux of final events
     */
    private Flux<AguiEvent> finishRun(EventConversionState state) {
        List<AguiEvent> events = new ArrayList<>();

        // End any messages that weren't properly ended
        for (String messageId : state.getStartedMessages()) {
            if (!state.hasEndedMessage(messageId)) {
                events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, messageId));
            }
        }

        // End any tool calls that weren't properly ended
        for (String toolCallId : state.getStartedToolCalls()) {
            if (!state.hasEndedToolCall(toolCallId)) {
                events.add(new AguiEvent.ToolCallEnd(state.threadId, state.runId, toolCallId));
            }
        }

        // End any reasoning messages that weren't properly ended
        for (String messageId : state.getStartedReasoningMessages()) {
            if (!state.hasEndedReasoningMessage(messageId)) {
                events.add(
                        new AguiEvent.ReasoningMessageEnd(state.threadId, state.runId, messageId));
            }
        }

        // Emit RUN_FINISHED
        events.add(new AguiEvent.RunFinished(state.threadId, state.runId));

        return Flux.fromIterable(events);
    }

    /**
     * Extract text content from a tool result block.
     *
     * @param toolResult The tool result block
     * @return The text content, or null if not present
     */
    private String extractToolResultText(ToolResultBlock toolResult) {
        if (toolResult.getOutput() == null || toolResult.getOutput().isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (ContentBlock output : toolResult.getOutput()) {
            if (output instanceof TextBlock textBlock) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(textBlock.getText());
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Serialize tool arguments to JSON string.
     *
     * @param input The tool input map
     * @return JSON string representation
     */
    private String serializeToolArgs(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        try {
            return JsonUtils.getJsonCodec().toJson(input);
        } catch (JsonException e) {
            return "{}";
        }
    }

    /**
     * State tracker for event conversion.
     * Uses LinkedHashSet to preserve insertion order for proper event sequencing.
     */
    private static class EventConversionState {
        final String threadId;
        final String runId;
        private final Set<String> startedMessages = new LinkedHashSet<>();
        private final Set<String> endedMessages = new LinkedHashSet<>();
        private final Set<String> startedToolCalls = new LinkedHashSet<>();
        private final Set<String> endedToolCalls = new LinkedHashSet<>();
        private final Set<String> startedReasoningMessages = new LinkedHashSet<>();
        private final Set<String> endedReasoningMessages = new LinkedHashSet<>();
        private String currentTextMessageId = null;
        private int compressionEventCount = 0;
        private boolean compressionStarted = false;

        EventConversionState(String threadId, String runId) {
            this.threadId = threadId;
            this.runId = runId;
        }

        boolean hasStartedMessage(String messageId) {
            return startedMessages.contains(messageId);
        }

        void startMessage(String messageId) {
            startedMessages.add(messageId);
            currentTextMessageId = messageId;
        }

        void endMessage(String messageId) {
            endedMessages.add(messageId);
            if (messageId.equals(currentTextMessageId)) {
                currentTextMessageId = null;
            }
        }

        boolean hasEndedMessage(String messageId) {
            return endedMessages.contains(messageId);
        }

        String getCurrentTextMessageId() {
            return currentTextMessageId;
        }

        boolean hasActiveTextMessage() {
            return currentTextMessageId != null && !hasEndedMessage(currentTextMessageId);
        }

        Set<String> getStartedMessages() {
            return startedMessages;
        }

        boolean hasStartedToolCall(String toolCallId) {
            return startedToolCalls.contains(toolCallId);
        }

        void startToolCall(String toolCallId) {
            startedToolCalls.add(toolCallId);
        }

        void endToolCall(String toolCallId) {
            endedToolCalls.add(toolCallId);
        }

        boolean hasEndedToolCall(String toolCallId) {
            return endedToolCalls.contains(toolCallId);
        }

        Set<String> getStartedToolCalls() {
            return startedToolCalls;
        }

        boolean hasStartedReasoningMessage(String messageId) {
            return startedReasoningMessages.contains(messageId);
        }

        void startReasoningMessage(String messageId) {
            startedReasoningMessages.add(messageId);
        }

        void endReasoningMessage(String messageId) {
            endedReasoningMessages.add(messageId);
        }

        boolean hasEndedReasoningMessage(String messageId) {
            return endedReasoningMessages.contains(messageId);
        }

        Set<String> getStartedReasoningMessages() {
            return startedReasoningMessages;
        }
    }
}

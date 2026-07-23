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

import com.hxh.apboa.engine.agui.AguiCustomEvents;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.log.telemetry.RunStatAccumulator;
import com.hxh.apboa.engine.log.telemetry.RunTelemetryExtractor;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agui.converter.AguiMessageConverter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
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

import reactor.core.publisher.Flux;

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
    private final Map<String, Map<String, Object>> TOOL_CACHE_MAP = new HashMap<>();

    /** 本轮是否因 HITL 确认而暂停（REASONING_STOP_REQUESTED）。供 processor 决定是否无条件保存暂停态。 */
    private volatile boolean suspended = false;

    /**
     * run 遥测（Adapter 实例即 run 级生命周期，事件流串行无并发）：
     * runStat 供收尾下发 RUN_META（与落库 meta 同源 RunStatAccumulator，起点差异见其 javadoc）；
     * subToolStartMs 为子智能体工具步计时（subToolUseId → 发起时刻，完成事件带 elapsed）
     */
    private final RunStatAccumulator runStat = new RunStatAccumulator();
    private final Map<String, Long> subToolStartMs = new HashMap<>();

    public boolean isSuspended() {
        return suspended;
    }

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

        return Flux.concat(
                        // Emit RUN_STARTED
                        Flux.just(new AguiEvent.RunStarted(threadId, runId)),
                        // Stream agent events and convert to AG-UI events
                        // Use concatMapIterable to preserve strict event ordering
                        agent.stream(msgs, options)
                                .concatMapIterable(event -> convertEvent(event, state)),
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

        // run 遥测：主 agent 每轮推理完成（isLast 事件带完整聚合 Msg，usage 已由框架写入 metadata）
        // 时累积轮次/token，供 finishRun 下发 RUN_META；子智能体转发块是 TOOL_RESULT 类型，天然不掺入
        if (type == EventType.REASONING && event.isLast() && msg != null) {
            runStat.onReasoningComplete(msg);
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
            if (event.isLast()) {
                // Handle tool results
                for (ContentBlock block : msg.getContent()) {
                    if (block instanceof ToolResultBlock toolResult) {
                        String toolCallId = toolResult.getId();
                        String result = extractToolResultText(toolResult);

                        boolean hasStarted = state.hasStartedToolCall(toolCallId);
                        if (!hasStarted) {
                            // 兜底补 ToolCallStart：常见于 HITL resume（每次 runWithMessages 新建 state，
                            // pause 前发过的 Start 状态在 resume 后已丢失）、子 agent tool 结果冒泡等场景。
                            // 从 ToolResultBlock 取真实 name（class 注释保证 messages 场景下 name required），
                            // 避免前端渲染成硬编码 "unknown"。
                            String name = toolResult.getName();
                            if (name == null || name.isEmpty()) {
                                name = "unknown";
                            }
                            events.add(
                                    new AguiEvent.ToolCallStart(
                                            state.threadId, state.runId, toolCallId, name));
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
            /*
             * 【子智能体流式事件】非 isLast 的 TOOL_RESULT = SubAgentTool.forwardEvent 转发的子智能体
             * 内部事件（原作者预留 TODO，现启用）。不解包混入主消息流（会与主 agent 消息混淆——
             * 原注释"前端适配效果不好"的根因），而是提取为与落库 subProcess 同构的步骤，
             * 经 Custom(SUBAGENT_STEP) 下发；前端按 parentToolCallId 关联到对应工具卡片。
             */
            else {
                for (ContentBlock block : msg.getContent()) {
                    if (block instanceof ToolResultBlock toolResult) {
                        Object subagentName = toolResult.getMetadata().get("subagent_name");
                        if (subagentName == null) {
                            continue;
                        }
                        for (ContentBlock contentBlock : toolResult.getOutput()) {
                            if (contentBlock instanceof TextBlock textBlock) {
                                emitSubagentSteps(events, state, textBlock.getText(), toolResult.getMetadata());
                            }
                        }
                    }
                }
            }

        } else if (type == EventType.AGENT_RESULT) {
            // HITL §6.2：推理后暂停（REASONING_STOP_REQUESTED）时推送 TOOL_CONFIRM_REQUIRED，
            // pending 精确为 need_confirm 工具——过滤掉同轮被 stopAgent 连累的普通/MCP 工具
            // （修 §2.2「MCP 确认假象」）。前端据此逐工具渲染「允许/禁止」，决策齐了调 /agui/resume。
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
                                    AguiCustomEvents.TOOL_CONFIRM_REQUIRED,
                                    Map.of("pending", pending)));
                }
            }
        }

        return events;
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

        // run 元数据（字段与落库 meta 同源自 RunStatAccumulator），流式收尾即时下发，前端免补拉
        if (runStat.hasData()) {
            events.add(
                    new AguiEvent.Custom(
                            state.threadId, state.runId, AguiCustomEvents.RUN_META, runStat.buildMeta()));
        }

        // Emit RUN_FINISHED
        events.add(new AguiEvent.RunFinished(state.threadId, state.runId));

        return Flux.fromIterable(events);
    }

    /**
     * 解析子智能体转发的内部事件，提取过程步骤并下发 SUBAGENT_STEP 自定义事件。
     *
     * <p>只处理完整节点（内层 isLast=true，过滤流式 chunk，一次子智能体运行约产生 轮数×2 条事件）；
     * 事件类型口径与落库侧（ChatLogHook 收集）一致：REASONING（思考/正文/工具发起）+
     * TOOL_RESULT（工具完成，按 subToolUseId 配对补 elapsed）。步骤结构由 RunTelemetryExtractor
     * 统一生成，与落库 subProcess 同构
     */
    private void emitSubagentSteps(
            List<AguiEvent> events,
            EventConversionState state,
            String innerEventJson,
            Map<String, Object> metadata) {
        Event subEvent;
        try {
            subEvent = JsonUtils.getJsonCodec().fromJson(innerEventJson, Event.class);
        } catch (Exception e) {
            // 内层事件反序列化失败静默跳过，不影响主流
            return;
        }
        if (subEvent == null || subEvent.getMessage() == null) {
            return;
        }
        Msg subMsg = subEvent.getMessage();
        EventType subType = subEvent.getType();

        // 流式片段（非 isLast）：子智能体 stream 为 incremental 模式，思考/正文块即增量文本，
        // 以 delta 步实时下发（前端逐字追加）；轮末 isLast 再发完整步定稿替换，保证与落库一致。
        // 工具发起（ToolUseBlock）只在 isLast 处理——生成中的 args 不完整
        if (!subEvent.isLast()) {
            if (subType == EventType.REASONING) {
                for (ContentBlock block : subMsg.getContent()) {
                    if (block instanceof ThinkingBlock thinkingBlock && !thinkingBlock.getThinking().isEmpty()) {
                        events.add(subagentStepEvent(state, metadata,
                                streamingDeltaStep("thinking", thinkingBlock.getThinking()), null));
                    } else if (block instanceof TextBlock textBlock && !textBlock.getText().isEmpty()) {
                        events.add(subagentStepEvent(state, metadata,
                                streamingDeltaStep("text", textBlock.getText()), null));
                    }
                }
            }
            return;
        }

        if (subType == EventType.REASONING) {
            RunTelemetryExtractor.visitReasoning(subMsg, new RunTelemetryExtractor.StepVisitor() {
                @Override
                public void onPlainStep(Map<String, Object> step) {
                    events.add(subagentStepEvent(state, metadata, step, null));
                }

                @Override
                public void onToolUse(ToolUseBlock block) {
                    Map<String, Object> step =
                            RunTelemetryExtractor.toolStep(block.getName(), block.getContent());
                    step.put("running", true);
                    String subToolUseId = block.getId();
                    if (subToolUseId != null) {
                        subToolStartMs.put(subToolUseId, System.currentTimeMillis());
                    }
                    events.add(subagentStepEvent(state, metadata, step, subToolUseId));
                }
            });
        } else if (subType == EventType.TOOL_RESULT) {
            RunTelemetryExtractor.visitToolResults(subMsg, toolResultBlock -> {
                String subToolUseId = toolResultBlock.getId();
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("type", "tool");
                step.put("name", toolResultBlock.getName());
                step.put("result", RunTelemetryExtractor.truncate(
                        RunTelemetryExtractor.toolResultText(toolResultBlock)));
                Long start = subToolUseId != null ? subToolStartMs.remove(subToolUseId) : null;
                if (start != null) {
                    step.put("elapsed", System.currentTimeMillis() - start);
                }
                events.add(subagentStepEvent(state, metadata, step, subToolUseId));
            });
        }
    }

    /** 流式增量步（delta 语义，前端逐字追加到同类型的进行中步骤） */
    private Map<String, Object> streamingDeltaStep(String type, String delta) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", type);
        step.put("delta", delta);
        return step;
    }

    /** 组装 SUBAGENT_STEP 事件（载荷契约见 AguiCustomEvents.SUBAGENT_STEP） */
    private AguiEvent subagentStepEvent(
            EventConversionState state,
            Map<String, Object> metadata,
            Map<String, Object> step,
            String subToolUseId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("parentToolCallId", metadata.getOrDefault("parent_tool_call_id", ""));
        value.put("subagentName", metadata.getOrDefault("subagent_name", ""));
        value.put("sessionId", metadata.getOrDefault("subagent_session_id", ""));
        if (subToolUseId != null) {
            value.put("subToolUseId", subToolUseId);
        }
        value.put("step", step);
        return new AguiEvent.Custom(state.threadId, state.runId, AguiCustomEvents.SUBAGENT_STEP, value);
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

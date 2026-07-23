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

import com.hxh.apboa.common.enums.ConfirmMode;
import com.hxh.apboa.engine.agui.AguiCustomEvents;
import com.hxh.apboa.engine.hitl.ConfirmModeResolver;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.log.ChatLogHook;
import com.hxh.apboa.engine.log.telemetry.RunStatAccumulator;
import com.hxh.apboa.engine.log.telemetry.RunTelemetryExtractor;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agui.converter.AguiMessageConverter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
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
     * runStat 供收尾下发 RUN_META（与落库 meta 同源 RunStatAccumulator，起点差异见其 javadoc）。
     * 工具耗时不在此计时——唯一计时者是 ChatLogHook（落库权威），实时下发经
     * pollToolElapsed 取走同一个值。
     */
    private final RunStatAccumulator runStat = new RunStatAccumulator();

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
                        // （含 AUTO_REJECT 模式的暂停自动全拒续跑，见 streamConverted）
                        streamConverted(msgs, options, state),
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
     * 跑一轮 agent.stream 并逐事件转换（concatMap 保序）。
     *
     * <p>「拒绝授权」模式（{@link ConfirmMode#AUTO_REJECT}）：检测到 HITL 确认暂停时不发
     * TOOL_CONFIRM_REQUIRED，而是直接下发拒绝工具结果事件（前端工具卡正常收尾、照常落库
     * tool 消息），并按人工全拒同款语义喂回 {@link AguiRequestProcessor#REJECT_RESULT_TEXT}
     * 就地续跑（agent 实例在内存，同一 state/SSE 流续接），递归处理可能的多次暂停——
     * 与 SubAgentTool 的挂起循环同构。模式实时读 Redis：挂起前用户切换即刻生效。
     */
    private Flux<AguiEvent> streamConverted(
            List<Msg> msgs, StreamOptions options, EventConversionState state) {
        return agent.stream(msgs, options)
                .concatMap(event -> {
                    List<ToolUseBlock> pending = confirmPausePending(event);
                    if (pending != null && !pending.isEmpty()
                            && ConfirmModeResolver.resolveByThreadId(state.threadId)
                                    == ConfirmMode.AUTO_REJECT) {
                        // 本次暂停已就地处理，重置挂起标记（否则 run 正常收尾仍被 processor
                        // 当暂停态保存）；续跑若再暂停且模式已改，会重新置位
                        this.suspended = false;
                        List<AguiEvent> rejectEvents = new ArrayList<>();
                        List<ContentBlock> rejectBlocks = new ArrayList<>();
                        for (ToolUseBlock toolUse : pending) {
                            String toolUseId = toolUse.getId();
                            if (!state.hasStartedToolCall(toolUseId)) {
                                rejectEvents.add(new AguiEvent.ToolCallStart(
                                        state.threadId, state.runId, toolUseId, toolUse.getName()));
                                state.startToolCall(toolUseId);
                            }
                            // 被拒工具无 PostActingEvent，tool 消息落库由 ChatLogHook 补偿
                            // （否则实时可见的拒绝工具卡刷新后丢失）；补偿返回的落库同源耗时
                            // 先于结果事件下发，前端只消费不掐表
                            Long authElapsed = ChatLogHook.completeMainToolRejected(
                                    state.threadId, toolUseId, AguiRequestProcessor.REJECT_RESULT_TEXT);
                            if (authElapsed != null) {
                                rejectEvents.add(new AguiEvent.Custom(
                                        state.threadId,
                                        state.runId,
                                        AguiCustomEvents.TOOL_ELAPSED,
                                        Map.of("toolUseId", toolUseId, "elapsed", authElapsed)));
                            }
                            rejectEvents.add(new AguiEvent.ToolCallEnd(
                                    state.threadId, state.runId, toolUseId));
                            rejectEvents.add(new AguiEvent.ToolCallResult(
                                    state.threadId,
                                    state.runId,
                                    toolUseId,
                                    AguiRequestProcessor.REJECT_RESULT_TEXT,
                                    "tool",
                                    event.getMessage().getId()));
                            state.endToolCall(toolUseId);
                            rejectBlocks.add(ToolResultBlock.of(
                                    toolUseId,
                                    toolUse.getName(),
                                    TextBlock.builder()
                                            .text(AguiRequestProcessor.REJECT_RESULT_TEXT)
                                            .build()));
                        }
                        List<Msg> rejectInput = List.of(
                                Msg.builder().role(MsgRole.TOOL).content(rejectBlocks).build());
                        return Flux.concat(
                                Flux.fromIterable(rejectEvents),
                                streamConverted(rejectInput, options, state));
                    }
                    return Flux.fromIterable(convertEvent(event, state));
                });
    }

    /**
     * 检测 HITL 确认暂停事件并提取待确认工具（口径与 AGENT_RESULT 转换分支一致：
     * 只取 isNeedConfirm 登记命中的，过滤同轮被 stopAgent 连累的普通工具）。
     *
     * @return 非暂停事件返回 null；暂停但无命中返回空列表（走常规转换）
     */
    private List<ToolUseBlock> confirmPausePending(Event event) {
        if (event.getType() != EventType.AGENT_RESULT) {
            return null;
        }
        Msg msg = event.getMessage();
        if (msg == null || msg.getGenerateReason() != GenerateReason.REASONING_STOP_REQUESTED) {
            return null;
        }
        List<ToolUseBlock> pending = new ArrayList<>();
        for (ToolUseBlock toolUse : msg.getContentBlocks(ToolUseBlock.class)) {
            if (IConfirmationHook.isNeedConfirm(toolUse.getName())) {
                pending.add(toolUse);
            }
        }
        return pending;
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

                        // 权威耗时先于结果事件下发（ChatLogHook 落库同源值；PostActing hook 在
                        // 事件进流前执行完毕，此刻表内已有值），前端只消费不掐表
                        Long authElapsed = ChatLogHook.pollToolElapsed(toolCallId);
                        if (authElapsed != null) {
                            events.add(new AguiEvent.Custom(
                                    state.threadId,
                                    state.runId,
                                    AguiCustomEvents.TOOL_ELAPSED,
                                    Map.of("toolUseId", toolCallId, "elapsed", authElapsed)));
                        }

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
                        Map<String, Object> metadata = toolResult.getMetadata();
                        Object subagentName = metadata.get("subagent_name");
                        if (subagentName == null) {
                            continue;
                        }
                        // 子智能体 HITL 确认请求块（SubAgentTool 挂起前冒泡，无 output）：
                        // 转为 Custom(SUBAGENT_CONFIRM_REQUIRED)，载荷契约见 AguiCustomEvents
                        if (Boolean.TRUE.equals(metadata.get("subagent_confirm"))) {
                            Map<String, Object> value = new LinkedHashMap<>();
                            value.put("parentToolCallId", metadata.getOrDefault("parent_tool_call_id", ""));
                            value.put("subagentName", subagentName);
                            value.put("subSessionId", metadata.getOrDefault("subagent_session_id", ""));
                            value.put("pending", metadata.getOrDefault("subagent_pending", List.of()));
                            events.add(new AguiEvent.Custom(
                                    state.threadId,
                                    state.runId,
                                    AguiCustomEvents.SUBAGENT_CONFIRM_REQUIRED,
                                    value));
                            continue;
                        }
                        // 后端直发的子过程步骤（HITL 拒绝结果等无真实事件可解析的场景，
                        // SubAgentTool 直接给出步骤体）：原样转为 SUBAGENT_STEP
                        Object directStep = metadata.get("subagent_step_direct");
                        if (directStep instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> stepMap = (Map<String, Object>) directStep;
                            Object subToolUseId = metadata.get("subagent_sub_tool_use_id");
                            events.add(subagentStepEvent(state, metadata, stepMap,
                                    subToolUseId == null ? null : String.valueOf(subToolUseId)));
                            continue;
                        }
                        for (ContentBlock contentBlock : toolResult.getOutput()) {
                            if (contentBlock instanceof TextBlock textBlock) {
                                emitSubagentSteps(events, state, textBlock.getText(), metadata);
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
                    events.add(subagentStepEvent(state, metadata, step, block.getId()));
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
                // 权威耗时：子 agent 的 PostActing hook（ChatLogHook 计时落库）先于本
                // TOOL_RESULT 事件转发到达，取走落库同一个值——实时与刷新后零误差
                Long authElapsed = ChatLogHook.pollToolElapsed(subToolUseId);
                if (authElapsed != null) {
                    step.put("elapsed", authElapsed);
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

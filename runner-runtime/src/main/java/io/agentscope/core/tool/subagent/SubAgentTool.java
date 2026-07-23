/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.tool.subagent;

import com.hxh.apboa.common.enums.ConfirmMode;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.hitl.ConfirmModeResolver;
import com.hxh.apboa.engine.hitl.PendingSubConfirmRegistry;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.log.ChatLogHook;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolEmitter;
import io.agentscope.core.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * AgentTool implementation that wraps a sub-agent for multi-turn conversation.
 *
 * <p>This tool allows an agent to be called as a tool by other agents, supporting multi-turn
 * conversation with session management. Each session maintains its own agent instance and state.
 *
 * <p>Thread safety is ensured by using {@link SubAgentProvider} to create a fresh agent instance
 * for each new session.
 *
 * <p>The tool exposes two parameters:
 *
 * <ul>
 *   <li>{@code session_id} - Optional. Omit to start a new session, provide to continue an
 *       existing one.
 *   <li>{@code message} - Required. The message to send to the agent.
 * </ul>
 */
public class SubAgentTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(SubAgentTool.class);

    /** Parameter name for session ID. */
    private static final String PARAM_SESSION_ID = "session_id";

    /** Parameter name for message. */
    private static final String PARAM_MESSAGE = "message";

    private final String name;
    private final String description;
    private final SubAgentProvider<?> agentProvider;
    private final SubAgentConfig config;

    /**
     * Creates a new SubAgentTool.
     *
     * @param agentProvider Provider for creating agent instances
     * @param config Configuration for the tool
     */
    public SubAgentTool(SubAgentProvider<?> agentProvider, SubAgentConfig config) {
        // Create a sample agent to derive name and description
        Agent sampleAgent = agentProvider.provide();

        this.agentProvider = agentProvider;
        this.config = config != null ? config : SubAgentConfig.defaults();
        this.name = resolveToolName(sampleAgent, this.config);
        this.description = resolveDescription(sampleAgent, this.config);

        logger.debug("Created SubAgentTool: name={}, description={}", name, description);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return buildSchema();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return executeConversation(param);
    }

    /**
     * Executes a conversation with the sub-agent, managing session lifecycle.
     *
     * <p>This method handles:
     *
     * <ul>
     *   <li>Session ID generation for new conversations
     *   <li>Agent state loading for continued sessions
     *   <li>Message execution (streaming or non-streaming based on config)
     *   <li>Agent state persistence after execution
     * </ul>
     *
     * @param param The tool call parameters containing input and emitter
     * @return A Mono emitting the tool result block
     */
    private Mono<ToolResultBlock> executeConversation(ToolCallParam param) {
        return Mono.deferContextual(
                (ctxView) -> {
                    try {
                        Map<String, Object> input = param.getInput();

                        // Get or create session ID
                        String sessionId = (String) input.get(PARAM_SESSION_ID);
                        boolean isNewSession = sessionId == null;
                        if (isNewSession) {
                            sessionId = UUID.randomUUID().toString();
                        }

                        // Get message
                        String message = (String) input.get(PARAM_MESSAGE);
                        if (message == null || message.isEmpty()) {
                            return Mono.just(ToolResultBlock.error("Message is required"));
                        }

                        // 初始化AgentContext加设置多租户必要上下文
                        AgentContext agentContext = param.getContext().get(AgentContext.class);
                        AgentContext.init(agentContext);
                        TenantUtils.setCurrentTenant(agentContext.getTenantId(), agentContext.getTenantCode());

                        // Create agent for this session
                        final String finalSessionId = sessionId;
                        Agent agent = agentProvider.provide();

                        // Load existing state if continuing session
                        if (!isNewSession && agent instanceof StateModule) {
                            loadAgentState(finalSessionId, (StateModule) agent);
                        }

                        // Build user message
                        Msg userMsg =
                                Msg.builder()
                                        .role(MsgRole.USER)
                                        .content(TextBlock.builder().text(message).build())
                                        .build();

                        logger.debug(
                                "Session {} with agent '{}': {}",
                                isNewSession ? "started" : "continued",
                                agent.getName(),
                                message.substring(0, Math.min(50, message.length())));

                        // Get emitter for event forwarding
                        ToolEmitter emitter = param.getEmitter();

                        // 主 agent 侧的工具调用 ID：随转发事件透传，供 AGUI 适配层把子智能体
                        // 步骤精确关联到对应的工具卡片（并行同名子智能体也不歧义）
                        String parentToolCallId = param.getToolUseBlock() != null
                                ? param.getToolUseBlock().getId()
                                : null;

                        // Execute and save state after completion
                        Mono<ToolResultBlock> result;
                        if (config.isForwardEvents()) {
                            result = executeWithStreaming(
                                    agent, userMsg, finalSessionId, emitter, parentToolCallId);
                        } else {
                            result = executeWithoutStreaming(agent, userMsg, finalSessionId);
                        }

                        // Save state after execution
                        return result.doOnSuccess(
                                r -> {
                                    if (agent instanceof StateModule) {
                                        saveAgentState(finalSessionId, (StateModule) agent);
                                    }
                                });
                    } catch (Exception e) {
                        logger.error("Error in session setup: {}", e.getMessage(), e);
                        return Mono.just(
                                ToolResultBlock.error("Session setup failed: " + e.getMessage()));
                    } finally {
                        AgentContext.clean();
                        TenantUtils.clear();
                    }
                });
    }

    /**
     * Loads agent state from the session storage.
     *
     * <p>If the session exists, the agent's state is restored. Any errors during loading are logged
     * but do not interrupt execution.
     *
     * @param sessionId The session ID to load state from
     * @param agent The state module to restore state into
     */
    private void loadAgentState(String sessionId, StateModule agent) {
        Session session = config.getSession();
        try {
            agent.loadIfExists(session, sessionId);
            logger.debug("Loaded state for session: {}", sessionId);
        } catch (Exception e) {
            logger.warn("Failed to load state for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Saves agent state to the session storage.
     *
     * <p>Persists the agent's current state. Any errors during saving are logged but do not
     * interrupt execution.
     *
     * @param sessionId The session ID to save state under
     * @param agent The state module to save state from
     */
    private void saveAgentState(String sessionId, StateModule agent) {
        Session session = config.getSession();
        try {
            agent.saveTo(session, sessionId);
            logger.debug("Saved state for session: {}", sessionId);
        } catch (Exception e) {
            logger.warn("Failed to save state for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Executes agent call with streaming, forwarding events to the emitter.
     *
     * <p>Uses the agent's streaming API and forwards each event to the provided emitter as JSON.
     * The final response is extracted from the last event.
     *
     * <p>HITL：子智能体内需确认工具触发暂停（REASONING_STOP_REQUESTED）时不再直接收尾
     * （旧行为返回 "(No response)"，确认被静默吞掉），而是把确认请求冒泡到主会话并挂起等待，
     * 用户决策后就地续跑，见 {@link #streamRound}。
     *
     * @param agent The agent to execute
     * @param userMsg The user message to send
     * @param sessionId The session ID for result building
     * @param emitter The emitter to forward events to
     * @return A Mono emitting the tool result block
     */
    private Mono<ToolResultBlock> executeWithStreaming(
            Agent agent, Msg userMsg, String sessionId, ToolEmitter emitter, String parentToolCallId) {

        StreamOptions streamOptions =
                config.getStreamOptions() != null
                        ? config.getStreamOptions()
                        : StreamOptions.defaults();

        return Mono.deferContextual(
                ctxView ->
                        streamRound(agent, List.of(userMsg), streamOptions, sessionId, emitter, parentToolCallId)
                                .contextWrite(context -> context.putAll(ctxView))
                                .onErrorResume(
                                        e -> {
                                            logger.error(
                                                    "Error in streaming execution:" + " {}",
                                                    e.getMessage(),
                                                    e);
                                            return Mono.just(
                                                    ToolResultBlock.error(
                                                            "Execution error: " + e.getMessage()));
                                        }));
    }

    /**
     * 跑一轮 agent.stream 并处理收尾：正常结束 → buildResult 照旧；HITL 确认暂停 →
     * 冒泡确认请求 + 挂起等待 + 决策后续跑并递归回本方法（一次子运行可能多次暂停）。
     *
     * <p>续跑输入与主流程 resume 同款语义（全允许=空列表继续 pending 工具；拒绝=喂回
     * TOOL 错误结果），子 agent 实例全程在内存，无需 loadFrom。
     */
    private Mono<ToolResultBlock> streamRound(
            Agent agent,
            List<Msg> input,
            StreamOptions streamOptions,
            String sessionId,
            ToolEmitter emitter,
            String parentToolCallId) {
        return agent.stream(input, streamOptions)
                .doOnNext(event -> forwardEvent(event, emitter, agent, sessionId, parentToolCallId))
                .filter(Event::isLast)
                .last()
                .flatMap(
                        lastEvent -> {
                            Msg response = lastEvent.getMessage();
                            if (response != null
                                    && response.getGenerateReason()
                                            == GenerateReason.REASONING_STOP_REQUESTED) {
                                return suspendForConfirmation(
                                        agent, response, streamOptions, sessionId, emitter, parentToolCallId);
                            }
                            return Mono.just(buildResult(response, sessionId));
                        });
    }

    /**
     * HITL 暂停处理：提取待确认工具 → 冒泡确认请求块 → 注册挂起等待主会话决策 → 续跑。
     *
     * <p>pending 口径与 AguiAgentAdapter AGENT_RESULT 分支一致：只取
     * {@link IConfirmationHook#isNeedConfirm} 登记命中的工具，过滤同轮被 stopAgent
     * 连累的普通工具。超时由 registry 兜底发全拒绝决策，走同一续跑路径。
     */
    private Mono<ToolResultBlock> suspendForConfirmation(
            Agent agent,
            Msg pausedMsg,
            StreamOptions streamOptions,
            String sessionId,
            ToolEmitter emitter,
            String parentToolCallId) {
        List<Map<String, Object>> pending = new ArrayList<>();
        for (ToolUseBlock toolUse : pausedMsg.getContentBlocks(ToolUseBlock.class)) {
            if (IConfirmationHook.isNeedConfirm(toolUse.getName())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("toolUseId", toolUse.getId());
                item.put("name", toolUse.getName());
                item.put("input", toolUse.getInput());
                pending.add(item);
            }
        }
        if (pending.isEmpty()) {
            // 防御：暂停但无需确认工具命中（理论不可达），按原收尾返回避免悬挂
            logger.warn("SubAgent session {} paused without confirmable tools", sessionId);
            return Mono.just(buildResult(pausedMsg, sessionId));
        }

        String parentThreadId = AgentMetadataStore.get(agent.getAgentId(), "subParentThreadId");

        // 「拒绝授权」模式：不挂起不冒泡，直接按人工全拒同款路径就地续跑
        // （模式实时读主会话 Redis，与主 agent 的 AguiAgentAdapter 自动拒绝对称）
        if (ConfirmModeResolver.resolveByThreadId(parentThreadId) == ConfirmMode.AUTO_REJECT) {
            logger.info("SubAgent session {} auto-rejected {} tools (confirm mode AUTO_REJECT)",
                    sessionId, pending.size());
            List<PendingSubConfirmRegistry.Decision> rejectAll = new ArrayList<>();
            for (Map<String, Object> tool : pending) {
                rejectAll.add(new PendingSubConfirmRegistry.Decision(
                        String.valueOf(tool.get("toolUseId")),
                        String.valueOf(tool.get("name")),
                        false));
            }
            return resumeAfterDecisions(
                    agent, rejectAll, streamOptions, sessionId, emitter, parentToolCallId);
        }

        PendingSubConfirmRegistry.PendingInfo info =
                new PendingSubConfirmRegistry.PendingInfo(
                        sessionId,
                        parentThreadId,
                        parentToolCallId,
                        agent.getName() == null ? "" : agent.getName(),
                        pending);

        // 先注册（register 调用即入表，决策先于订阅到达也会被 Sinks.One 缓存重放）再冒泡，
        // 避免前端秒回决策时 complete 查不到挂起条目
        Mono<List<PendingSubConfirmRegistry.Decision>> decisionsMono =
                PendingSubConfirmRegistry.register(info);
        emitConfirmRequired(emitter, agent, sessionId, parentToolCallId, pending);

        return decisionsMono.flatMap(
                decisions ->
                        resumeAfterDecisions(
                                agent, decisions, streamOptions, sessionId, emitter, parentToolCallId));
    }

    /**
     * 按决策续跑（人工决策到达 / 超时全拒 / 拒绝授权模式短路 共用）：
     * 被拒的工具不会真实执行、无 PostActingEvent 也无 TOOL_RESULT 事件——
     * 落库侧直接补偿工具步 result（否则历史渲染兜底显示「完成」造成误导），
     * 实时侧经转发通道直发拒绝完成步（否则前端只剩本地占位文案，与落库不同构）；
     * 然后按主 resume 同款语义构造输入就地续跑。
     */
    private Mono<ToolResultBlock> resumeAfterDecisions(
            Agent agent,
            List<PendingSubConfirmRegistry.Decision> decisions,
            StreamOptions streamOptions,
            String sessionId,
            ToolEmitter emitter,
            String parentToolCallId) {
        logger.info(
                "SubAgent session {} confirm decided: {} approved / {} total",
                sessionId,
                decisions.stream().filter(PendingSubConfirmRegistry.Decision::approved).count(),
                decisions.size());
        for (PendingSubConfirmRegistry.Decision d : decisions) {
            if (d != null && !d.approved()) {
                Long elapsed = ChatLogHook.completeSubToolStepRejected(
                        agent.getAgentId(),
                        d.toolUseId(),
                        AguiRequestProcessor.REJECT_RESULT_TEXT);
                emitRejectedStep(emitter, agent, sessionId, parentToolCallId, d, elapsed);
            }
        }
        return streamRound(
                agent,
                buildSubResumeInput(decisions),
                streamOptions,
                sessionId,
                emitter,
                parentToolCallId);
    }

    /**
     * 把确认请求经 {@link #forwardEvent} 同款事件通道冒泡给主流：AguiAgentAdapter 识别
     * metadata 的 subagent_confirm 标记后转为 Custom(SUBAGENT_CONFIRM_REQUIRED) 下发前端。
     */
    private void emitConfirmRequired(
            ToolEmitter emitter,
            Agent agent,
            String sessionId,
            String parentToolCallId,
            List<Map<String, Object>> pending) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("subagent_confirm", true);
            metadata.put("subagent_pending", pending);
            metadata.put("subagent_name", agent.getName() == null ? "" : agent.getName());
            metadata.put("subagent_id", agent.getAgentId() == null ? "" : agent.getAgentId());
            metadata.put("subagent_session_id", sessionId == null ? "" : sessionId);
            metadata.put("parent_tool_call_id", parentToolCallId == null ? "" : parentToolCallId);
            emitter.emit(new ToolResultBlock(null, null, List.of(), metadata));
        } catch (Exception e) {
            logger.warn("Failed to emit sub-agent confirm request: {}", e.getMessage());
        }
    }

    /**
     * 拒绝的工具无真实执行、不会产生可转发的 TOOL_RESULT 事件，把「工具完成（拒绝）」
     * 步骤经 metadata 直发标记冒泡给主流：AguiAgentAdapter 识别 subagent_step_direct 后
     * 原样转为 Custom(SUBAGENT_STEP)，前端按 subToolUseId 配对合并——实时显示与落库
     * 同构（文案同 {@link AguiRequestProcessor#REJECT_RESULT_TEXT}、elapsed 同落库补偿值）。
     */
    private void emitRejectedStep(
            ToolEmitter emitter,
            Agent agent,
            String sessionId,
            String parentToolCallId,
            PendingSubConfirmRegistry.Decision decision,
            Long elapsed) {
        try {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("type", "tool");
            step.put("name", decision.name());
            step.put("result", AguiRequestProcessor.REJECT_RESULT_TEXT);
            if (elapsed != null) {
                step.put("elapsed", elapsed);
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("subagent_step_direct", step);
            metadata.put("subagent_sub_tool_use_id", decision.toolUseId());
            metadata.put("subagent_name", agent.getName() == null ? "" : agent.getName());
            metadata.put("subagent_id", agent.getAgentId() == null ? "" : agent.getAgentId());
            metadata.put("subagent_session_id", sessionId == null ? "" : sessionId);
            metadata.put("parent_tool_call_id", parentToolCallId == null ? "" : parentToolCallId);
            emitter.emit(new ToolResultBlock(null, null, List.of(), metadata));
        } catch (Exception e) {
            logger.warn("Failed to emit rejected step: {}", e.getMessage());
        }
    }

    /**
     * 按主流程 resume 同款语义构造续跑输入（对齐 AguiRequestProcessor.buildResumeInput）：
     * 全允许 → 空列表，agent 继续执行暂停前的 pending 工具；含拒绝 → 喂回「授权被拒/工具
     * 不可用」错误结果（文案复用 {@link AguiRequestProcessor#REJECT_RESULT_TEXT}，
     * 防模型把拒绝理解成"重试"），允许的仍留给 agent 自己执行。
     */
    private List<Msg> buildSubResumeInput(List<PendingSubConfirmRegistry.Decision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return List.of();
        }
        List<ContentBlock> rejects = new ArrayList<>();
        for (PendingSubConfirmRegistry.Decision d : decisions) {
            if (d != null && !d.approved()) {
                rejects.add(
                        ToolResultBlock.of(
                                d.toolUseId(),
                                d.name(),
                                TextBlock.builder()
                                        .text(AguiRequestProcessor.REJECT_RESULT_TEXT)
                                        .build()));
            }
        }
        if (rejects.isEmpty()) {
            return List.of();
        }
        return List.of(Msg.builder().role(MsgRole.TOOL).content(rejects).build());
    }

    /**
     * Executes agent call without streaming.
     *
     * <p>Uses the agent's standard call API. No events are forwarded to the emitter.
     *
     * @param agent The agent to execute
     * @param userMsg The user message to send
     * @param sessionId The session ID for result building
     * @return A Mono emitting the tool result block
     */
    private Mono<ToolResultBlock> executeWithoutStreaming(
            Agent agent, Msg userMsg, String sessionId) {

        return Mono.deferContextual(
                ctxView ->
                        agent.call(List.of(userMsg))
                                .map(response -> buildResult(response, sessionId))
                                .onErrorResume(
                                        e -> {
                                            logger.error(
                                                    "Error in execution: {}", e.getMessage(), e);
                                            return Mono.just(
                                                    ToolResultBlock.error(
                                                            "Execution error: " + e.getMessage()));
                                        })
                                .contextWrite(context -> context.putAll(ctxView)));
    }

    /**
     * Forwards an event to the emitter as serialized JSON.
     *
     * <p>Serializes the event using JsonCodec and emits it as a text block. Serialization
     * failures are logged but do not interrupt execution.
     *
     * @param event The event to forward
     * @param emitter The emitter to send the event to
     * @param agent The agent
     * @param sessionId Current session ID
     */
    private void forwardEvent(
            Event event, ToolEmitter emitter, Agent agent, String sessionId, String parentToolCallId) {
        try {
            String json = JsonUtils.getJsonCodec().toJson(event);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("subagent_event", event == null ? "" : event);
            metadata.put("subagent_name", agent.getName() == null ? "" : agent.getName());
            metadata.put("subagent_id", agent.getAgentId() == null ? "" : agent.getAgentId());
            metadata.put("subagent_session_id", sessionId == null ? "" : sessionId);
            metadata.put("parent_tool_call_id", parentToolCallId == null ? "" : parentToolCallId);
            emitter.emit(
                    new ToolResultBlock(
                            null, null, List.of(TextBlock.builder().text(json).build()), metadata));
        } catch (Exception e) {
            logger.warn("Failed to serialize event to JSON: {}", e.getMessage());
        }
    }

    /**
     * Builds the final tool result with session context.
     *
     * <p>Formats the response to include the session ID, allowing callers to continue the
     * conversation by passing the session ID in subsequent calls.
     *
     * @param response The agent's response message
     * @param sessionId The session ID to include in the result
     * @return A tool result block containing the formatted response
     */
    private ToolResultBlock buildResult(Msg response, String sessionId) {
        String textContent = response.getTextContent();

        // Return response with session context
        return ToolResultBlock.text(
                String.format(
                        "session_id: %s\n\n%s",
                        sessionId, textContent != null ? textContent : "(No response)"));
    }

    /**
     * Builds the JSON schema for tool parameters.
     *
     * <p>Creates a schema with two properties:
     *
     * <ul>
     *   <li>{@code session_id} - Optional string for continuing existing conversations
     *   <li>{@code message} - Required string containing the message to send
     * </ul>
     *
     * @return A map representing the JSON schema for tool parameters
     */
    private Map<String, Object> buildSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // Session ID (optional)
        Map<String, Object> sessionIdProp = new HashMap<>();
        sessionIdProp.put("type", "string");
        sessionIdProp.put(
                "description",
                "Session ID for multi-turn dialogue. Omit to start a NEW session."
                        + " To CONTINUE an existing session and retain memory, you MUST extract"
                        + " the session_id from the previous response and pass it here.");
        properties.put(PARAM_SESSION_ID, sessionIdProp);

        // Message (required)
        Map<String, Object> messageProp = new HashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "Message to send to the agent");
        properties.put(PARAM_MESSAGE, messageProp);

        schema.put("properties", properties);
        schema.put("required", List.of(PARAM_MESSAGE));

        return schema;
    }

    /**
     * Resolves the tool name from config or derives it from the agent.
     *
     * <p>Priority: explicit config.toolName > derived from agent name.
     * If derived from the agent name, the name will be sanitized to comply with strict LLM API constraints
     * (e.g., ^[a-zA-Z0-9_-]{1,64}$). For non-English characters (like Chinese) or excessively long names,
     * a deterministic short hash of the original name is appended to prevent naming collisions.
     *
     * @param agent The agent to derive name from if not configured
     * @param config The configuration that may override the name
     * @return The resolved tool name
     */
    private String resolveToolName(Agent agent, SubAgentConfig config) {
        if (config.getToolName() != null && !config.getToolName().trim().isEmpty()) {
            return config.getToolName().trim();
        }

        if (agent.getName() == null || agent.getName().trim().isEmpty()) {
            return "call_agent";
        }

        return sanitizeName("call_", agent.getName().trim());
    }

    /**
     * Helper method for {@link #resolveToolName(Agent, SubAgentConfig)}.
     * Extracts valid characters, lazily computes a deterministic hash
     * if necessary, and strictly enforces length limits via safe truncation.
     *
     * @param prefix The prefix to prepend to the tool name (e.g., "call_").
     * @param originalName The original name of the agent.
     * @return A sanitized, safe-to-use tool name.
     */
    private String sanitizeName(String prefix, String originalName) {
        // Keep the underscore, replace other illegal characters with underscores uniformly,
        // merge consecutive underscores, and remove the first and last underscores
        String lowerOriginal = originalName.toLowerCase(Locale.ROOT);
        String safePart =
                lowerOriginal
                        .replaceAll("[^a-z0-9_-]+", "_")
                        .replaceAll("_+", "_")
                        .replaceAll("^_+|_+$", "");

        if (safePart.isEmpty()) {
            safePart = "agent";
        }

        String resolvedName = prefix + safePart;
        boolean isInformationLost = lowerOriginal.matches("^[a-z0-9_\\-\\s]+$");

        boolean needsHash = !isInformationLost || resolvedName.length() > 64;

        if (needsHash) {
            // Generate deterministic hash
            UUID uuid = UUID.nameUUIDFromBytes(originalName.getBytes(StandardCharsets.UTF_8));
            String shortHash = uuid.toString().replace("-", "").substring(0, 8);
            String suffix = "_" + shortHash;

            logger.warn(
                    "Agent name '{}' contains unsupported characters or is too long. Appended hash"
                        + " '{}' to prevent collisions. Only alphanumeric characters, underscores,"
                        + " and hyphens are supported in generated names. Recommended to configure"
                        + " an explicit English 'toolName' via SubAgentConfig.",
                    originalName,
                    shortHash);

            resolvedName = prefix + safePart + suffix;

            if (resolvedName.length() > 64) {
                int allowedSafePartLen = 64 - prefix.length() - suffix.length();
                if (allowedSafePartLen > 0) {
                    // replaceAll("_+$", "") strips any trailing underscores created by the cut,
                    // preventing double underscores when the suffix is appended.
                    safePart = safePart.substring(0, allowedSafePartLen).replaceAll("_+$", "");
                    resolvedName = prefix + safePart + suffix;
                } else {
                    // If prefix + suffix alone exceeds or equals 64 characters,
                    // discard the safePart entirely and forcefully truncate the prefix + hash
                    // combination.
                    resolvedName =
                            (prefix + shortHash)
                                    .substring(
                                            0, Math.min(64, prefix.length() + shortHash.length()));
                }
            }
        }

        return resolvedName;
    }

    /**
     * Resolves the tool description from config or derives it from the agent.
     *
     * <p>Priority: config.description > agent.description > default. The default description is
     * generated as "Call {agentName} to complete tasks".
     *
     * @param agent The agent to derive description from if not configured
     * @param config The configuration that may override the description
     * @return The resolved description
     */
    private String resolveDescription(Agent agent, SubAgentConfig config) {
        if (config.getDescription() != null && !config.getDescription().isEmpty()) {
            return config.getDescription();
        }
        // Use agent description if available
        String agentDesc = agent.getDescription();
        if (agentDesc != null && !agentDesc.isEmpty()) {
            return agentDesc;
        }
        // Generate default description
        return "Call " + agent.getName() + " to complete tasks";
    }
}

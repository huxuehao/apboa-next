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

import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.subagent.SubAgentTraceEvent;
import com.hxh.apboa.common.subagent.SubAgentTraceEventType;
import com.hxh.apboa.engine.agui.AgentContext;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolEmitter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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

    /** @deprecated Use {@link SubAgentTraceEvent#FINAL_RESULT_METADATA_KEY}. */
    @Deprecated
    public static final String FINAL_TRACE_METADATA_KEY = SubAgentTraceEvent.FINAL_RESULT_METADATA_KEY;

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
                        // The trace captures the immutable invocation identity before streaming starts.
                        SubAgentTraceContext trace = SubAgentTraceContext.create(
                                param.getToolUseBlock(), param.getAgent(), agentContext, agent, finalSessionId);
                        emitTrace(emitter, trace, SubAgentTraceEventType.STARTED, Map.of(
                                "task", message,
                                "toolName", name));

                        // Execute and save state after completion
                        Mono<ToolResultBlock> result;
                        if (config.isForwardEvents()) {
                            result = executeWithStreaming(agent, userMsg, finalSessionId, emitter, trace);
                        } else {
                            result = executeWithoutStreaming(agent, userMsg, finalSessionId, trace);
                        }

                        // 设置 AgentId 到 AgentMetadataStore
                        String childAgentId;
                        if (agent instanceof AgentBase agentBase) {
                            childAgentId = agentBase.getAgentId();

                            AgentMetadataStore.put(childAgentId, "threadId", agentContext.getThreadId());
                            AgentMetadataStore.put(childAgentId, "runId", agentContext.getRunId());
                            AgentMetadataStore.put(childAgentId, "tenantId", agentContext.getTenantId());
                            AgentMetadataStore.put(childAgentId, "tenantCode", agentContext.getTenantCode());
                            AgentMetadataStore.put(childAgentId, "cleanUpOnOwn", true);
                            AgentMetadataStore.put(
                                    childAgentId,
                                    "subagentInvocationId",
                                    param.getToolUseBlock() == null ? null : param.getToolUseBlock().getId());
                        } else {
                            childAgentId = null;
                        }

                        // Save state after execution
                        return result.doOnSuccess(
                                r -> {
                                    if (agent instanceof StateModule) {
                                        saveAgentState(finalSessionId, (StateModule) agent);
                                    }
                                    emitTrace(emitter, trace, SubAgentTraceEventType.FINISHED, Map.of(
                                            "summary", extractResultText(r),
                                            "status", "SUCCESS"));
                                }).onErrorResume(error -> {
                                    logger.error("Error in sub-agent execution: {}", safeMessage(error), error);
                                    emitTrace(emitter, trace, SubAgentTraceEventType.FAILED, Map.of(
                                            "message", safeMessage(error),
                                            "status", "FAILED"));
                                    return Mono.just(ToolResultBlock.error(
                                            "Execution error: " + safeMessage(error)));
                                })
                                .doOnCancel(() -> emitTrace(emitter, trace, SubAgentTraceEventType.CANCELLED, Map.of(
                                        "status", "CANCELLED")))
                                .doFinally(signalType -> {
                                    if (childAgentId != null) {
                                        AgentMetadataStore.removeOnOwn(childAgentId);
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
     * @param agent The agent to execute
     * @param userMsg The user message to send
     * @param sessionId The session ID for result building
     * @param emitter The emitter to forward events to
     * @return A Mono emitting the tool result block
     */
    private Mono<ToolResultBlock> executeWithStreaming(
            Agent agent,
            Msg userMsg,
            String sessionId,
            ToolEmitter emitter,
            SubAgentTraceContext trace) {

        StreamOptions streamOptions =
                config.getStreamOptions() != null
                        ? config.getStreamOptions()
                        : StreamOptions.defaults();

        return Mono.deferContextual(
                ctxView ->
                        agent.stream(List.of(userMsg), streamOptions)
                                .doOnNext(event -> forwardEvent(event, emitter, trace))
                                .filter(Event::isLast)
                                .last()
                                .map(
                                        lastEvent -> {
                                            Msg response = lastEvent.getMessage();
                                            return buildResult(response, sessionId, trace);
                                        })
                                .contextWrite(context -> context.putAll(ctxView)));
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
            Agent agent, Msg userMsg, String sessionId, SubAgentTraceContext trace) {

        return Mono.deferContextual(
                ctxView ->
                        agent.call(List.of(userMsg))
                                .map(response -> buildResult(response, sessionId, trace))
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
    private void forwardEvent(Event event, ToolEmitter emitter, SubAgentTraceContext trace) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        try {
            for (ContentBlock block : event.getMessage().getContent()) {
                if (block instanceof TextBlock textBlock) {
                    emitTrace(emitter, trace,
                            event.isLast() ? SubAgentTraceEventType.MESSAGE_COMPLETED : SubAgentTraceEventType.MESSAGE_DELTA,
                            Map.of("messageId", event.getMessage().getId(), "role", "assistant",
                                    "content", textBlock.getText(), "isLast", event.isLast()));
                } else if (block instanceof ThinkingBlock thinkingBlock) {
                    emitTrace(emitter, trace,
                            event.isLast() ? SubAgentTraceEventType.MESSAGE_COMPLETED : SubAgentTraceEventType.MESSAGE_DELTA,
                            Map.of("messageId", event.getMessage().getId(), "role", "thinking",
                                    "content", thinkingBlock.getThinking(), "isLast", event.isLast()));
                } else if (block instanceof ToolUseBlock toolUse) {
                    String toolCallId = toolUse.getId();
                    if (trace.markToolStarted(toolCallId)) {
                        emitTrace(emitter, trace, SubAgentTraceEventType.TOOL_STARTED, payload(
                                "toolCallId", toolCallId,
                                "name", toolUse.getName(),
                                "args", toolUse.getContent()));
                    } else {
                        emitTrace(emitter, trace, SubAgentTraceEventType.TOOL_ARGUMENTS, payload(
                                "toolCallId", toolCallId,
                                "args", toolUse.getContent()));
                    }
                } else if (block instanceof ToolResultBlock toolResult) {
                    emitTrace(emitter, trace, SubAgentTraceEventType.TOOL_COMPLETED, payload(
                            "toolCallId", toolResult.getId(),
                            "name", toolResult.getName(),
                            "result", extractToolResultText(toolResult)));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to forward sub-agent trace event: {}", e.getMessage());
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
    private ToolResultBlock buildResult(Msg response, String sessionId, SubAgentTraceContext trace) {
        String textContent = response.getTextContent();

        // Return response with session context
        return new ToolResultBlock(
                null,
                null,
                List.of(TextBlock.builder().text(String.format(
                        "session_id: %s\n\n%s",
                        sessionId, textContent != null ? textContent : "(No response)")) .build()),
                Map.of(SubAgentTraceEvent.FINAL_RESULT_METADATA_KEY, trace.getInvocationId()));
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                result.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        }
        return result;
    }

    private static String extractResultText(ToolResultBlock result) {
        return result == null ? "" : extractToolResultText(result);
    }

    private static String extractToolResultText(ToolResultBlock result) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : result.getOutput()) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                if (text.length() > 0) text.append('\n');
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }

    private static String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null) return "Sub-agent execution failed";
        return error.getMessage();
    }

    private static void emitTrace(
            ToolEmitter emitter,
            SubAgentTraceContext trace,
            SubAgentTraceEventType eventType,
            Map<String, Object> eventPayload) {
        if (emitter == null || trace == null) return;
        emitter.emit(new ToolResultBlock(
                null,
                null,
                List.of(),
                Map.of(SubAgentTraceEvent.METADATA_KEY, trace.next(eventType, eventPayload))));
    }

    private static final class SubAgentTraceContext {
        private final String invocationId;
        private final String rootRunId;
        private final String parentInvocationId;
        private final SubAgentTraceEvent.Agent agent;
        private final AtomicLong sequence = new AtomicLong();
        private final Set<String> startedToolCallIds = ConcurrentHashMap.newKeySet();

        private SubAgentTraceContext(
                String invocationId,
                String rootRunId,
                String parentInvocationId,
                SubAgentTraceEvent.Agent agent) {
            this.invocationId = invocationId == null || invocationId.isBlank() ? UUID.randomUUID().toString() : invocationId;
            this.rootRunId = rootRunId;
            this.parentInvocationId = parentInvocationId;
            this.agent = agent;
        }

        static SubAgentTraceContext create(
                ToolUseBlock toolUse,
                Agent parentAgent,
                AgentContext context,
                Agent agent,
                String subagentSessionId) {
            SubAgentTraceEvent.Agent traceAgent = new SubAgentTraceEvent.Agent();
            traceAgent.setRuntimeId(agent == null ? null : agent.getAgentId());
            traceAgent.setCode(agent == null ? null : agent.getName());
            // Keep the card header concise and stable; the execution task carries the full context.
            traceAgent.setTitle(agent == null ? null : agent.getName());
            traceAgent.setSubagentSessionId(subagentSessionId);

            String parentInvocationId = null;
            if (parentAgent != null) {
                parentInvocationId = AgentMetadataStore.get(parentAgent.getAgentId(), "subagentInvocationId");
            }
            return new SubAgentTraceContext(
                    toolUse == null ? null : toolUse.getId(),
                    context == null ? null : context.getRunId(),
                    parentInvocationId,
                    traceAgent);
        }

        SubAgentTraceEvent next(SubAgentTraceEventType type, Map<String, Object> eventPayload) {
            long currentSequence = sequence.incrementAndGet();
            SubAgentTraceEvent event = new SubAgentTraceEvent();
            event.setInvocationId(invocationId);
            event.setRootRunId(rootRunId);
            event.setParentInvocationId(parentInvocationId);
            event.setSequence(currentSequence);
            event.setEventId(invocationId + ":" + currentSequence);
            event.setOccurredAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            event.setEventType(type);
            event.setAgent(agent);
            event.setPayload(eventPayload);
            return event;
        }

        String getInvocationId() {
            return invocationId;
        }

        boolean markToolStarted(String toolCallId) {
            // ToolUseBlock ids are expected, but retain a deterministic per-trace fallback.
            String key = toolCallId == null || toolCallId.isBlank()
                    ? "anonymous"
                    : toolCallId;
            return startedToolCallIds.add(key);
        }
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

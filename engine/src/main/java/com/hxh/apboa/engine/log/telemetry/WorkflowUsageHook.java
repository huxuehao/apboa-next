package com.hxh.apboa.engine.log.telemetry;

import com.hxh.apboa.node.agent.AgentModelRequestTrace;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 工作流智能体节点的 run 级用量累计 hook：节点带工具时 ReAct 会多轮调用 LLM，
 * 最终 Msg 的 ChatUsage 只含末轮用量，须逐轮（PostReasoning）累计才不低估 token。
 *
 * <p>工作流节点每次执行新建 agent 与本 hook 实例（不同于跨 run 共享的 ChatLogHook，
 * 无需按 threadId 建 static 映射），事件流内串行喂入，无并发。
 *
 * @author huxuehao
 */
public class WorkflowUsageHook implements Hook {

    private static final int TOOL_ARGUMENT_MAX_LEN = 2048;
    private static final int TOOL_DETAIL_MAX_LEN = 512;

    private final RunStatAccumulator accumulator = new RunStatAccumulator();
    private final List<MutableRound> rounds = new ArrayList<>();
    private final Map<String, MutableToolCall> toolCallsById = new HashMap<>();
    private final Map<ToolUseBlock, MutableToolCall> toolCallsByIdentity = new IdentityHashMap<>();

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        synchronized (this) {
            if (event instanceof PostReasoningEvent postReasoningEvent) {
                onReasoning(postReasoningEvent.getReasoningMessage());
            } else if (event instanceof PreActingEvent preActingEvent) {
                findOrCreateToolCall(preActingEvent.getToolUse()).markStarted();
            } else if (event instanceof PostActingEvent postActingEvent) {
                findOrCreateToolCall(postActingEvent.getToolUse())
                        .markFinished(postActingEvent.getToolResult());
            }
        }
        return Mono.just(event);
    }

    public RunStatAccumulator getAccumulator() {
        return accumulator;
    }

    public synchronized List<RoundTelemetry> snapshotRounds() {
        return rounds.stream().map(MutableRound::snapshot).toList();
    }

    private void onReasoning(Msg message) {
        if (message == null) {
            return;
        }
        accumulator.onReasoningComplete(message);
        ChatUsage usage = message.getChatUsage();
        int requestIndex = rounds.size() + 1;
        int thinkingChars = message.getContentBlocks(ThinkingBlock.class).stream()
                .mapToInt(block -> block.getThinking().length())
                .sum();
        MutableRound round = new MutableRound(
                requestIndex,
                usage == null ? null : usage.getInputTokens(),
                usage == null ? null : usage.getOutputTokens(),
                usage == null ? null : usage.getTotalTokens(),
                usage == null ? null : usage.getTime(),
                message.getGenerateReason() == null ? null : message.getGenerateReason().name(),
                thinkingChars);
        rounds.add(round);

        for (ToolUseBlock toolUse : message.getContentBlocks(ToolUseBlock.class)) {
            MutableToolCall toolCall = new MutableToolCall(toolUse);
            round.toolCalls.add(toolCall);
            toolCallsByIdentity.put(toolUse, toolCall);
            if (toolUse.getId() != null && !toolUse.getId().isBlank()) {
                toolCallsById.put(toolUse.getId(), toolCall);
            }
        }
    }

    private MutableToolCall findOrCreateToolCall(ToolUseBlock toolUse) {
        if (toolUse != null && toolUse.getId() != null && !toolUse.getId().isBlank()) {
            MutableToolCall byId = toolCallsById.get(toolUse.getId());
            if (byId != null) {
                return byId;
            }
        }
        MutableToolCall byIdentity = toolCallsByIdentity.get(toolUse);
        if (byIdentity != null) {
            return byIdentity;
        }

        MutableToolCall created = new MutableToolCall(toolUse);
        MutableRound target = rounds.isEmpty()
                ? new MutableRound(1, null, null, null, null, null, 0)
                : rounds.getLast();
        if (rounds.isEmpty()) {
            rounds.add(target);
        }
        target.toolCalls.add(created);
        if (toolUse != null) {
            toolCallsByIdentity.put(toolUse, created);
            if (toolUse.getId() != null && !toolUse.getId().isBlank()) {
                toolCallsById.put(toolUse.getId(), created);
            }
        }
        return created;
    }

    public record RoundTelemetry(
            int requestIndex,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Double modelTimeSeconds,
            String generateReason,
            Integer thinkingChars,
            List<AgentModelRequestTrace.ToolCall> toolCalls) {}

    private static final class MutableRound {
        private final int requestIndex;
        private final Integer inputTokens;
        private final Integer outputTokens;
        private final Integer totalTokens;
        private final Double modelTimeSeconds;
        private final String generateReason;
        private final Integer thinkingChars;
        private final List<MutableToolCall> toolCalls = new ArrayList<>();

        private MutableRound(
                int requestIndex,
                Integer inputTokens,
                Integer outputTokens,
                Integer totalTokens,
                Double modelTimeSeconds,
                String generateReason,
                Integer thinkingChars) {
            this.requestIndex = requestIndex;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.modelTimeSeconds = modelTimeSeconds;
            this.generateReason = generateReason;
            this.thinkingChars = thinkingChars;
        }

        private RoundTelemetry snapshot() {
            return new RoundTelemetry(
                    requestIndex,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    modelTimeSeconds,
                    generateReason,
                    thinkingChars,
                    toolCalls.stream().map(MutableToolCall::snapshot).toList());
        }
    }

    private static final class MutableToolCall {
        private final String id;
        private final String name;
        private final String arguments;
        private Long startedAt;
        private Long finishedAt;
        private String status = "REQUESTED";
        private String detail;

        private MutableToolCall(ToolUseBlock toolUse) {
            this.id = toolUse == null ? null : toolUse.getId();
            this.name = toolUse == null ? null : toolUse.getName();
            String rawArguments = null;
            if (toolUse != null) {
                rawArguments = toolUse.getContent();
                if (rawArguments == null || rawArguments.isBlank()) {
                    rawArguments = String.valueOf(toolUse.getInput());
                }
            }
            this.arguments = truncate(rawArguments, TOOL_ARGUMENT_MAX_LEN);
        }

        private void markStarted() {
            if (startedAt == null) {
                startedAt = System.currentTimeMillis();
            }
            status = "RUNNING";
        }

        private void markFinished(ToolResultBlock result) {
            if (startedAt == null) {
                startedAt = System.currentTimeMillis();
            }
            finishedAt = System.currentTimeMillis();
            String resultText = RunTelemetryExtractor.toolResultText(result);
            if (result != null && result.isSuspended()) {
                status = "SUSPENDED";
            } else if (resultText != null && resultText.stripLeading().startsWith("Error:")) {
                status = "FAIL";
                detail = truncate(resultText, TOOL_DETAIL_MAX_LEN);
            } else {
                status = "SUCCESS";
            }
        }

        private AgentModelRequestTrace.ToolCall snapshot() {
            Long elapsed = startedAt == null || finishedAt == null
                    ? null
                    : Math.max(0, finishedAt - startedAt);
            return new AgentModelRequestTrace.ToolCall(
                    id, name, arguments, status, elapsed, detail);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }
}

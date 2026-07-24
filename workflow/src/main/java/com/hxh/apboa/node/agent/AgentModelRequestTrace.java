package com.hxh.apboa.node.agent;

import java.util.List;
import java.util.Map;

/**
 * 工作流智能体节点的单轮模型请求遥测。
 *
 * <p>字段会随节点 executionContext 落入 workflow_node_execution.process_data，
 * 不保存完整思考或工具结果正文，只保留定位性能与调用路径所需的有界摘要。
 */
public record AgentModelRequestTrace(
        int requestIndex,
        int maxAttempts,
        String status,
        Long durationMs,
        Long ttftMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double modelTimeSeconds,
        String finishReason,
        String generateReason,
        Integer thinkingChars,
        Map<String, Object> providerMetrics,
        List<Attempt> attempts,
        List<ToolCall> toolCalls) {

    public AgentModelRequestTrace {
        providerMetrics = providerMetrics == null ? Map.of() : Map.copyOf(providerMetrics);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public record Attempt(
            int attempt,
            String status,
            Long elapsed,
            Long ttft,
            String detail) {}

    public record ToolCall(
            String id,
            String name,
            String arguments,
            String status,
            Long elapsed,
            String detail) {}
}

package com.hxh.apboa.engine.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.entity.WorkflowNodeExecution;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.engine.log.telemetry.RunTelemetryExtractor;
import com.hxh.apboa.node.agent.AgentModelRequestTrace;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对话工具消息中的工作流最终过程快照。
 *
 * <p>节点部分只认 {@link WorkflowRunResult#getNodeExecutions()}，按引擎真实执行轨迹输出，
 * 不根据画布拓扑猜测；模型尝试记录来自同一 toolUseId 下的可观察进度，并按 nodeId +
 * nodeInvocationId 归入对应节点，因此循环中同一节点的多次执行也不会混在一起。
 */
public record WorkflowProcessSnapshot(
        String runId,
        String workflowId,
        String version,
        String status,
        Long elapsed,
        String error,
        List<NodeSnapshot> nodes) {

    public static final String METADATA_KEY = "workflow_process";

    public static WorkflowProcessSnapshot from(
            WorkflowRunResult result, List<ToolProgressBridge.Progress> progress) {
        if (result == null) {
            return failed("工作流未返回执行结果");
        }
        WorkflowRun run = result.getRun();
        Map<String, Deque<List<AgentModelRequestTrace>>> modelRequestsByNode =
                buildModelRequests(progress);
        List<NodeSnapshot> nodes = new ArrayList<>();
        List<WorkflowNodeExecution> executions = result.getNodeExecutions() == null
                ? List.of()
                : result.getNodeExecutions();
        for (WorkflowNodeExecution execution : executions) {
            Deque<List<AgentModelRequestTrace>> invocations =
                    modelRequestsByNode.get(execution.getNodeId());
            List<AgentModelRequestTrace> liveModelRequests = invocations == null || invocations.isEmpty()
                    ? List.of()
                    : invocations.pollFirst();
            List<AgentModelRequestTrace> persistedModelRequests =
                    extractModelRequests(execution.getProcessData());
            List<AgentModelRequestTrace> modelRequests = persistedModelRequests.isEmpty()
                    ? liveModelRequests
                    : persistedModelRequests;
            nodes.add(new NodeSnapshot(
                    execution.getNodeId(),
                    execution.getNodeTitle(),
                    execution.getNodeType() == null ? null : execution.getNodeType().name(),
                    execution.getStatus() == null ? null : execution.getStatus().name(),
                    duration(execution.getStartTime(), execution.getEndTime()),
                    RunTelemetryExtractor.truncate(execution.getInputs()),
                    RunTelemetryExtractor.truncate(execution.getOutputs()),
                    RunTelemetryExtractor.truncate(execution.getError()),
                    extractModelName(execution.getProcessData()),
                    modelRequests));
        }
        return new WorkflowProcessSnapshot(
                run == null || run.getId() == null ? null : String.valueOf(run.getId()),
                run == null ? null : run.getWorkflowId(),
                run == null ? null : run.getVersion(),
                run == null || run.getStatus() == null ? null : run.getStatus().name(),
                run == null ? null : duration(run.getStartTime(), run.getEndTime()),
                run == null ? null : RunTelemetryExtractor.truncate(run.getError()),
                List.copyOf(nodes));
    }

    public static WorkflowProcessSnapshot failed(String error) {
        return new WorkflowProcessSnapshot(
                null, null, null, "FAIL", null,
                RunTelemetryExtractor.truncate(error), List.of());
    }

    private static Map<String, Deque<List<AgentModelRequestTrace>>> buildModelRequests(
            List<ToolProgressBridge.Progress> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        List<ToolProgressBridge.Progress> ordered = source.stream()
                .filter(WorkflowProcessSnapshot::isModelProgress)
                .filter(p -> p.getNodeId() != null && !p.getNodeId().isBlank())
                .sorted(Comparator.comparingLong(
                        p -> p.getOccurredAt() == null ? Long.MAX_VALUE : p.getOccurredAt()))
                .toList();

        Map<String, LinkedHashMap<String, List<ToolProgressBridge.Progress>>> grouped =
                new LinkedHashMap<>();
        for (ToolProgressBridge.Progress progress : ordered) {
            String invocationId = progress.getNodeInvocationId();
            if (invocationId == null || invocationId.isBlank()) {
                invocationId = progress.getNodeId() + "#default";
            }
            grouped.computeIfAbsent(progress.getNodeId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(invocationId, ignored -> new ArrayList<>())
                    .add(progress);
        }

        Map<String, Deque<List<AgentModelRequestTrace>>> result = new LinkedHashMap<>();
        grouped.forEach((nodeId, invocationMap) -> {
            Deque<List<AgentModelRequestTrace>> queue = new ArrayDeque<>();
            invocationMap.values().forEach(events -> queue.addLast(toModelRequests(events)));
            result.put(nodeId, queue);
        });
        return result;
    }

    private static List<AgentModelRequestTrace> toModelRequests(
            List<ToolProgressBridge.Progress> events) {
        Map<Integer, List<ToolProgressBridge.Progress>> byRequest = new TreeMap<>();
        for (ToolProgressBridge.Progress event : events) {
            int requestIndex = event.getRequestIndex() == null ? 1 : event.getRequestIndex();
            byRequest.computeIfAbsent(requestIndex, ignored -> new ArrayList<>()).add(event);
        }

        List<AgentModelRequestTrace> requests = new ArrayList<>();
        byRequest.forEach((requestIndex, requestEvents) -> {
            Map<Integer, MutableAttempt> attempts = new TreeMap<>();
            int maxAttempts = 1;
            long lastOccurredAt = 0;
            for (ToolProgressBridge.Progress event : requestEvents) {
                int eventAttempt = event.getAttempt() == null ? 1 : event.getAttempt();
                maxAttempts = Math.max(maxAttempts,
                        event.getMaxAttempts() == null ? eventAttempt : event.getMaxAttempts());
                long occurredAt = event.getOccurredAt() == null ? lastOccurredAt : event.getOccurredAt();
                lastOccurredAt = Math.max(lastOccurredAt, occurredAt);
                if ("MODEL_RETRYING".equals(event.getPhase())) {
                    int failedAttempt = Math.max(1, eventAttempt - 1);
                    MutableAttempt attempt = attempts.computeIfAbsent(
                            failedAttempt, MutableAttempt::new);
                    attempt.finish("FAIL", event.getElapsed(), event.getDetail(), occurredAt);
                    continue;
                }
                MutableAttempt attempt = attempts.computeIfAbsent(
                        eventAttempt, MutableAttempt::new);
                if ("MODEL_WAITING".equals(event.getPhase()) && attempt.startedAt == null) {
                    attempt.startedAt = occurredAt;
                } else if ("MODEL_GENERATING".equals(event.getPhase())
                        && attempt.firstTokenAt == null) {
                    attempt.firstTokenAt = occurredAt;
                } else if ("MODEL_SUCCEEDED".equals(event.getPhase())) {
                    attempt.finish("SUCCESS", event.getElapsed(), null, occurredAt);
                } else if ("MODEL_FAILED".equals(event.getPhase())) {
                    attempt.finish("FAIL", event.getElapsed(), event.getDetail(), occurredAt);
                }
            }
            long fallbackFinishedAt = lastOccurredAt;
            List<AgentModelRequestTrace.Attempt> attemptSnapshots = attempts.values().stream()
                    .map(attempt -> attempt.snapshot(fallbackFinishedAt))
                    .toList();
            Long requestStartedAt = attempts.values().stream()
                    .map(attempt -> attempt.startedAt)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            Long requestFinishedAt = attempts.values().stream()
                    .map(attempt -> attempt.finishedAt)
                    .filter(java.util.Objects::nonNull)
                    .reduce((first, second) -> second)
                    .orElse(null);
            Long firstTokenAt = attempts.values().stream()
                    .map(attempt -> attempt.firstTokenAt)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            String status = attemptSnapshots.isEmpty()
                    ? "STOP"
                    : attemptSnapshots.getLast().status();
            requests.add(new AgentModelRequestTrace(
                    requestIndex,
                    maxAttempts,
                    status,
                    duration(requestStartedAt, requestFinishedAt),
                    duration(requestStartedAt, firstTokenAt),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    attemptSnapshots,
                    List.of()));
        });
        return List.copyOf(requests);
    }

    private static boolean isModelProgress(ToolProgressBridge.Progress progress) {
        return progress != null
                && progress.getPhase() != null
                && progress.getPhase().startsWith("MODEL_");
    }

    private static Long duration(Long start, Long end) {
        if (start == null || end == null) {
            return null;
        }
        return Math.max(0, end - start);
    }

    private static String extractModelName(String processData) {
        if (processData == null || processData.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JsonUtils.parse(processData);
            JsonNode modelName = node == null ? null : node.get("modelName");
            return modelName == null || modelName.isNull() ? null : modelName.asText();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<AgentModelRequestTrace> extractModelRequests(String processData) {
        if (processData == null || processData.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JsonUtils.parse(processData);
            JsonNode modelRequests = root == null ? null : root.get("modelRequests");
            if (modelRequests == null || !modelRequests.isArray() || modelRequests.isEmpty()) {
                return List.of();
            }
            List<AgentModelRequestTrace> parsed = JsonUtils.parse(
                    modelRequests.toString(),
                    new TypeReference<List<AgentModelRequestTrace>>() {});
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public record NodeSnapshot(
            String nodeId,
            String title,
            String type,
            String status,
            Long elapsed,
            String inputs,
            String outputs,
            String error,
            String modelName,
            List<AgentModelRequestTrace> modelRequests) {}

    private static final class MutableAttempt {
        private final int attempt;
        private Long startedAt;
        private Long firstTokenAt;
        private Long finishedAt;
        private Long elapsed;
        private String status;
        private String detail;

        private MutableAttempt(int attempt) {
            this.attempt = attempt;
        }

        private void finish(String status, Long elapsed, String detail, long finishedAt) {
            this.status = status;
            this.elapsed = elapsed;
            this.detail = RunTelemetryExtractor.truncate(detail);
            this.finishedAt = finishedAt;
        }

        private AgentModelRequestTrace.Attempt snapshot(long fallbackFinishedAt) {
            Long duration = elapsed;
            if (duration == null && startedAt != null) {
                long end = finishedAt == null ? fallbackFinishedAt : finishedAt;
                duration = Math.max(0, end - startedAt);
            }
            Long ttft = startedAt == null || firstTokenAt == null
                    ? null
                    : Math.max(0, firstTokenAt - startedAt);
            return new AgentModelRequestTrace.Attempt(
                    attempt,
                    status == null ? "STOP" : status,
                    duration,
                    ttft,
                    detail);
        }
    }
}

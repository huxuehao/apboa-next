package com.hxh.apboa.engine.model;

import com.hxh.apboa.engine.tool.ToolProgressBridge;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * 仅用于“对话调用工作流”场景的模型进度装饰器。
 *
 * <p>AgentScope 的默认重试封装在模型内部，外层看不到每次 retry。这里把 delegate 的单请求
 * 重试降为 1 次，再按同样的 3 次、指数退避、同一可重试异常口径在可观察层重放，因而既不
 * 改变默认容错语义，又能把每一次等待、开始生成和重试准确下发到工作流工具卡片。
 */
public final class WorkflowProgressModel implements Model {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
    private static final Set<String> PROVIDER_METRIC_KEYS = Set.of(
            "total_duration",
            "load_duration",
            "prompt_eval_count",
            "prompt_eval_duration",
            "eval_count",
            "eval_duration");

    private final Model delegate;
    private final String toolUseId;
    private final String nodeId;
    private final String nodeName;
    private final String nodeInvocationId;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final AtomicInteger requestSequence = new AtomicInteger(0);
    private final Map<Integer, RequestState> requestTimings = new ConcurrentSkipListMap<>();

    public WorkflowProgressModel(Model delegate, String toolUseId) {
        this(delegate, toolUseId, null, null, null, INITIAL_BACKOFF, MAX_BACKOFF);
    }

    public WorkflowProgressModel(
            Model delegate,
            String toolUseId,
            String nodeId,
            String nodeName,
            String nodeInvocationId) {
        this(delegate, toolUseId, nodeId, nodeName, nodeInvocationId,
                INITIAL_BACKOFF, MAX_BACKOFF);
    }

    WorkflowProgressModel(
            Model delegate,
            String toolUseId,
            Duration initialBackoff,
            Duration maxBackoff) {
        this(delegate, toolUseId, null, null, null, initialBackoff, maxBackoff);
    }

    WorkflowProgressModel(
            Model delegate,
            String toolUseId,
            String nodeId,
            String nodeName,
            String nodeInvocationId,
            Duration initialBackoff,
            Duration maxBackoff) {
        this.delegate = delegate;
        this.toolUseId = toolUseId;
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.nodeInvocationId = nodeInvocationId;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        // request 级配置优先，把 delegate 内部重试关闭；其余生成参数继续从原请求/模型默认值继承。
        GenerateOptions singleAttemptOptions =
                GenerateOptions.builder()
                        .executionConfig(
                                ExecutionConfig.builder()
                                        .timeout(REQUEST_TIMEOUT)
                                        .maxAttempts(1)
                                        .retryOn(ExecutionConfig.RETRYABLE_ERRORS)
                                        .build())
                        .build();
        GenerateOptions effectiveOptions =
                GenerateOptions.mergeOptions(singleAttemptOptions, options);
        int requestIndex = requestSequence.incrementAndGet();
        RequestState requestState = new RequestState(requestIndex);
        requestTimings.put(requestIndex, requestState);
        AtomicInteger attempt = new AtomicInteger(0);
        AtomicLong attemptStartedAt = new AtomicLong(0);

        Flux<ChatResponse> observableAttempt = Flux.defer(() -> {
            int currentAttempt = attempt.incrementAndGet();
            long startedAt = System.currentTimeMillis();
            attemptStartedAt.set(startedAt);
            AttemptState attemptState = requestState.startAttempt(currentAttempt, startedAt);
            emit(ToolProgressBridge.Progress.builder()
                    .phase("MODEL_WAITING")
                    .message("模型排队 / 等待响应")
                    .attempt(currentAttempt)
                    .maxAttempts(MAX_ATTEMPTS)
                    .requestIndex(requestIndex)
                    .build());
            AtomicBoolean firstChunk = new AtomicBoolean(true);
            return delegate.stream(messages, tools, effectiveOptions)
                    .doOnNext(response -> {
                        long occurredAt = System.currentTimeMillis();
                        requestState.observe(response);
                        // OpenAI 流首帧常只有 role，末帧也可能只有 usage；只把第一个
                        // 实际内容块计作首 token，避免把“首 SSE 帧”误报成 TTFT。
                        if (hasGeneratedContent(response)
                                && firstChunk.compareAndSet(true, false)) {
                            attemptState.markFirstToken(occurredAt);
                            emit(ToolProgressBridge.Progress.builder()
                                    .phase("MODEL_GENERATING")
                                    .message("模型正在生成")
                                    .attempt(currentAttempt)
                                    .maxAttempts(MAX_ATTEMPTS)
                                    .requestIndex(requestIndex)
                                    .build());
                        }
                    })
                    .doOnComplete(() -> {
                        long completedAt = System.currentTimeMillis();
                        attemptState.finish("SUCCESS", completedAt, null);
                        requestState.finish("SUCCESS", completedAt);
                        emit(ToolProgressBridge.Progress.builder()
                                .phase("MODEL_SUCCEEDED")
                                .message("模型请求完成")
                                .attempt(currentAttempt)
                                .maxAttempts(MAX_ATTEMPTS)
                                .requestIndex(requestIndex)
                                .elapsed(elapsedSince(attemptStartedAt.get()))
                                .build());
                    })
                    .doOnError(error -> {
                        long failedAt = System.currentTimeMillis();
                        String detail = errorDetail(error);
                        attemptState.finish("FAIL", failedAt, detail);
                        requestState.finish("FAIL", failedAt);
                    });
        });

        return observableAttempt
                .retryWhen(
                        Retry.backoff(MAX_ATTEMPTS - 1, initialBackoff)
                                .maxBackoff(maxBackoff)
                                .jitter(0.5)
                                .filter(ExecutionConfig.RETRYABLE_ERRORS)
                                .doBeforeRetry(signal -> {
                                    int nextAttempt = (int) signal.totalRetries() + 2;
                                    emit(ToolProgressBridge.Progress.builder()
                                            .phase("MODEL_RETRYING")
                                            .message("模型请求失败，正在重试（"
                                                    + nextAttempt + "/" + MAX_ATTEMPTS + "）")
                                            .attempt(nextAttempt)
                                            .maxAttempts(MAX_ATTEMPTS)
                                            .requestIndex(requestIndex)
                                            .elapsed(elapsedSince(attemptStartedAt.get()))
                                            .detail(errorDetail(signal.failure()))
                                            .build());
                                }))
                .doOnError(error -> emit(ToolProgressBridge.Progress.builder()
                        .phase("MODEL_FAILED")
                        .message("模型请求失败")
                        .attempt(Math.min(attempt.get(), MAX_ATTEMPTS))
                        .maxAttempts(MAX_ATTEMPTS)
                        .requestIndex(requestIndex)
                        .elapsed(elapsedSince(attemptStartedAt.get()))
                        .detail(errorDetail(error))
                        .build()));
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }

    /**
     * 获取当前节点各轮模型调用的稳定快照；不依赖 ToolProgressBridge，独立运行同样可落库。
     */
    public List<RequestTiming> snapshotRequestTimings() {
        return requestTimings.values().stream()
                .map(RequestState::snapshot)
                .toList();
    }

    private void emit(ToolProgressBridge.Progress progress) {
        ToolProgressBridge.emit(toolUseId, progress.toBuilder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .nodeInvocationId(nodeInvocationId)
                .build());
    }

    private static long elapsedSince(long startedAt) {
        return startedAt <= 0 ? 0 : Math.max(0, System.currentTimeMillis() - startedAt);
    }

    private static boolean hasGeneratedContent(ChatResponse response) {
        return response != null
                && response.getContent() != null
                && !response.getContent().isEmpty();
    }

    private static String errorDetail(Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable current = error;
        while ((current.getMessage() == null || current.getMessage().isBlank())
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        // 避免上游把超长响应体整段塞进进度事件和页面。
        return message.length() > 240 ? message.substring(0, 240) + "…" : message;
    }

    public record RequestTiming(
            int requestIndex,
            int maxAttempts,
            String status,
            Long durationMs,
            Long ttftMs,
            String finishReason,
            Map<String, Object> providerMetrics,
            List<AttemptTiming> attempts) {}

    public record AttemptTiming(
            int attempt,
            String status,
            Long elapsed,
            Long ttft,
            String detail) {}

    private static final class RequestState {
        private final int requestIndex;
        private final Map<Integer, AttemptState> attempts = new TreeMap<>();
        private final Map<String, Object> providerMetrics = new LinkedHashMap<>();
        private Long startedAt;
        private Long firstTokenAt;
        private Long finishedAt;
        private String status = "RUNNING";
        private String finishReason;

        private RequestState(int requestIndex) {
            this.requestIndex = requestIndex;
        }

        private synchronized AttemptState startAttempt(int attempt, long timestamp) {
            if (startedAt == null) {
                startedAt = timestamp;
            }
            status = "RUNNING";
            finishedAt = null;
            AttemptState state = new AttemptState(attempt, timestamp);
            attempts.put(attempt, state);
            return state;
        }

        private synchronized void observe(ChatResponse response) {
            if (response == null) {
                return;
            }
            if (response.getFinishReason() != null && !response.getFinishReason().isBlank()) {
                finishReason = response.getFinishReason();
            }
            Map<String, Object> metadata = response.getMetadata();
            if (metadata != null) {
                PROVIDER_METRIC_KEYS.forEach(key -> {
                    Object value = metadata.get(key);
                    if (value instanceof Number || value instanceof String || value instanceof Boolean) {
                        providerMetrics.put(key, value);
                    }
                });
            }
        }

        private synchronized void finish(String status, long timestamp) {
            this.status = status;
            this.finishedAt = timestamp;
            this.firstTokenAt = attempts.values().stream()
                    .map(AttemptState::firstTokenAt)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        private synchronized RequestTiming snapshot() {
            Long duration = startedAt == null || finishedAt == null
                    ? null
                    : Math.max(0, finishedAt - startedAt);
            Long ttft = startedAt == null || firstTokenAt == null
                    ? null
                    : Math.max(0, firstTokenAt - startedAt);
            return new RequestTiming(
                    requestIndex,
                    MAX_ATTEMPTS,
                    status,
                    duration,
                    ttft,
                    finishReason,
                    Map.copyOf(providerMetrics),
                    attempts.values().stream().map(AttemptState::snapshot).toList());
        }
    }

    private static final class AttemptState {
        private final int attempt;
        private final long startedAt;
        private Long firstTokenAt;
        private Long finishedAt;
        private String status = "RUNNING";
        private String detail;

        private AttemptState(int attempt, long startedAt) {
            this.attempt = attempt;
            this.startedAt = startedAt;
        }

        private synchronized void markFirstToken(long timestamp) {
            if (firstTokenAt == null) {
                firstTokenAt = timestamp;
            }
        }

        private synchronized void finish(String status, long timestamp, String detail) {
            this.status = status;
            this.finishedAt = timestamp;
            this.detail = detail;
        }

        private synchronized Long firstTokenAt() {
            return firstTokenAt;
        }

        private synchronized AttemptTiming snapshot() {
            Long elapsed = finishedAt == null ? null : Math.max(0, finishedAt - startedAt);
            Long ttft = firstTokenAt == null ? null : Math.max(0, firstTokenAt - startedAt);
            return new AttemptTiming(attempt, status, elapsed, ttft, detail);
        }
    }
}

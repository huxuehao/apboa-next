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
package io.agentscope.core.tool;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.hitl.EditedInputApplier;
import com.hxh.apboa.engine.log.ChatLogHook;
import com.hxh.apboa.engine.tool.ToolProgressBridge;
import com.hxh.apboa.engine.tool.WorkflowProcessSnapshot;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.util.ExceptionUtils;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/**
 * Unified executor for tool execution with infrastructure concerns.
 *
 * <p>This class consolidates all tool execution logic including:
 * <ul>
 *   <li>Single and batch tool execution</li>
 *   <li>Parallel/sequential execution control</li>
 *   <li>Timeout and retry handling</li>
 *   <li>Thread scheduling</li>
 *   <li>Schema validation before execution</li>
 * </ul>
 *
 * <p>Execution modes:
 * <ul>
 *   <li>Default: Uses Reactor's Schedulers.boundedElastic() for async I/O operations</li>
 *   <li>Custom: Uses user-provided ExecutorService</li>
 * </ul>
 */
class ToolExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ToolExecutor.class);

    private final Toolkit toolkit;
    private final ToolRegistry toolRegistry;
    private final ToolGroupManager groupManager;
    private final ToolkitConfig config;
    private final ExecutorService executorService;
    private BiConsumer<ToolUseBlock, ToolResultBlock> userChunkCallback;
    private BiConsumer<ToolUseBlock, ToolResultBlock> internalChunkCallback;

    /**
     * Create a tool executor with Reactor Schedulers (recommended).
     */
    ToolExecutor(
            Toolkit toolkit,
            ToolRegistry toolRegistry,
            ToolGroupManager groupManager,
            ToolkitConfig config) {
        this(toolkit, toolRegistry, groupManager, config, null);
    }

    /**
     * Create a tool executor with custom executor service.
     */
    ToolExecutor(
            Toolkit toolkit,
            ToolRegistry toolRegistry,
            ToolGroupManager groupManager,
            ToolkitConfig config,
            ExecutorService executorService) {
        this.toolkit = toolkit;
        this.toolRegistry = toolRegistry;
        this.groupManager = groupManager;
        this.config = config;
        this.executorService = executorService;
    }

    /**
     * Set the user-defined chunk callback for streaming tool responses.
     */
    void setChunkCallback(BiConsumer<ToolUseBlock, ToolResultBlock> callback) {
        this.userChunkCallback = callback;
    }

    /**
     * Set the framework-internal chunk callback used by ReActAgent hooks.
     */
    void setInternalChunkCallback(BiConsumer<ToolUseBlock, ToolResultBlock> callback) {
        this.internalChunkCallback = callback;
    }

    /**
     * Get the user-defined chunk callback.
     * Used by Toolkit.copy() to preserve user callbacks during deep copy.
     */
    BiConsumer<ToolUseBlock, ToolResultBlock> getChunkCallback() {
        return this.userChunkCallback;
    }

    /**
     * Combine the user-defined and internal chunk callbacks.
     */
    private BiConsumer<ToolUseBlock, ToolResultBlock> getEffectiveChunkCallback() {
        if (internalChunkCallback == null) {
            return userChunkCallback != null
                    ? (toolUse, chunk) ->
                    invokeChunkCallback("user", userChunkCallback, toolUse, chunk)
                    : null;
        }
        if (userChunkCallback == null) {
            return (toolUse, chunk) ->
                    invokeChunkCallback("internal", internalChunkCallback, toolUse, chunk);
        }
        return (toolUse, chunk) -> {
            invokeChunkCallback("internal", internalChunkCallback, toolUse, chunk);
            invokeChunkCallback("user", userChunkCallback, toolUse, chunk);
        };
    }

    /**
     * Invoke a chunk callback without allowing it to block other callbacks.
     */
    private void invokeChunkCallback(
            String callbackType,
            BiConsumer<ToolUseBlock, ToolResultBlock> callback,
            ToolUseBlock toolUse,
            ToolResultBlock chunk) {
        try {
            callback.accept(toolUse, chunk);
        } catch (Exception e) {
            logger.warn(
                    "Chunk callback '{}' failed for tool '{}': {}",
                    callbackType,
                    toolUse.getName(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    e);
        }
    }

    // ==================== Single Tool Execution ====================

    /**
     * Execute a single tool call with full infrastructure support.
     *
     * @param param Tool call parameters
     * @return Mono containing execution result
     */
    Mono<ToolResultBlock> execute(ToolCallParam param) {
        return TracerRegistry.get().callTool(this.toolkit, param, () -> executeCore(param));
    }

    /**
     * Core tool execution logic.
     *
     * <p>This method handles:
     * <ul>
     *   <li>Tool lookup and validation</li>
     *   <li>Group activation check</li>
     *   <li>Parameter merging (preset + input)</li>
     *   <li>Context merging</li>
     *   <li>Schema validation</li>
     *   <li>Actual tool invocation</li>
     * </ul>
     */
    private Mono<ToolResultBlock> executeCore(ToolCallParam param) {
        ToolUseBlock toolCall = param.getToolUseBlock();
        AgentTool tool = toolRegistry.getTool(toolCall.getName());

        // 检查 workspace hook 错误
        Object error = param.getToolUseBlock().getMetadata().get(SysConst.WORKSPACE_HOOK_ERROR_KEY);
        if (error != null) {
            return Mono.just(ToolResultBlock.text(error.toString()));
        }

        if (tool == null) {
            return Mono.just(ToolResultBlock.error("Tool not found: " + toolCall.getName()));
        }

        // Check tool activation
        RegisteredToolFunction registered = toolRegistry.getRegisteredTool(toolCall.getName());
        if (registered != null && !groupManager.isActiveTool(toolCall.getName())) {
            String errorMsg =
                    String.format(
                            "Unauthorized tool call: '%s' is not available", toolCall.getName());
            logger.warn(errorMsg);
            return Mono.just(ToolResultBlock.error(errorMsg));
        }

        // Validate input against schema
        String validationError =
                ToolValidator.validateInput(toolCall.getContent(), tool.getParameters());
        if (validationError != null) {
            String errorMsg =
                    String.format(
                            "Parameter validation failed for tool '%s': %s\n"
                                    + "Please correct the parameters and try again.",
                            toolCall.getName(), validationError);
            logger.debug(errorMsg);
            return Mono.just(ToolResultBlock.error(errorMsg));
        }

        // Merge context
        ToolExecutionContext toolkitContext = config.getDefaultContext();
        ToolExecutionContext finalContext =
                ToolExecutionContext.merge(param.getContext(), toolkitContext);

        // Create emitter for streaming
        ToolEmitter toolEmitter = new DefaultToolEmitter(toolCall, getEffectiveChunkCallback());

        // Merge input with preset parameters. Preset values win so framework-controlled
        // parameters remain immutable from the caller/LLM perspective.
        Map<String, Object> mergedInput = new HashMap<>();
        if (!param.getInput().isEmpty()) {
            mergedInput.putAll(param.getInput());
        } else if (!toolCall.getInput().isEmpty()) {
            mergedInput.putAll(toolCall.getInput());
        }
        if (registered != null) {
            mergedInput.putAll(registered.getPresetParameters());
        }

        // Build final execution param
        ToolCallParam executionParam =
                ToolCallParam.builder()
                        .toolUseBlock(toolCall)
                        .input(mergedInput)
                        .agent(param.getAgent())
                        .context(finalContext)
                        .emitter(toolEmitter)
                        .build();

        AgentContext agentContext = finalContext.get(AgentContext.class);
        return Mono.defer(() -> {
                    // 在订阅线程设置租户（若 callAsync 内部不切换线程，则可生效）
                    if (agentContext != null) {
                        TenantUtils.setCurrentTenant(agentContext.getTenantId(), agentContext.getTenantCode());
                    }
                    return tool.callAsync(executionParam);
                })
                // HITL 改参：结果尾部追加「用户改参」提示（挂起/异常路径在下方 onErrorResume 产生，不经过此 map）
                .map(result -> appendUserEditedNote(toolCall, result))
                .onErrorResume(
                        ToolSuspendException.class,
                        e -> {
                            // Convert ToolSuspendException to suspended result
                            logger.debug(
                                    "Tool '{}' suspended: {}",
                                    toolCall.getName(),
                                    e.getReason() != null ? e.getReason() : "no reason");
                            return Mono.just(ToolResultBlock.suspended(toolCall, e));
                        })
                .onErrorResume(
                        e -> {
                            String errorMsg =
                                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                            return Mono.just(ToolResultBlock.error("Tool execution failed: " + errorMsg));
                        })
                .doFinally(signalType -> TenantUtils.clear());   // 无论成功、挂起、异常都清理
    }

    /**
     * HITL 改参提示：拼在改参调用的执行结果尾部。改写记忆抹掉了"用户修改"事件，
     * 模型面对「用户原话 ↔ 执行参数/结果」的冲突会归因"自己传错了"并重调纠正
     * （实测行为）；证据注入在困惑产生的准确位置（读结果处），消解为"用户改了主意"。
     * 语义强化措辞与 REJECT_RESULT_TEXT 同理：明确禁止按早前表述重试。
     */
    /** 改参提示模板：%s 为字段级修改摘要（仅用户实际改动的字段，其余参数仍是模型原始生成值） */
    private static final String USER_EDITED_NOTE_TEMPLATE =
            "\n\n[系统提示] 本次调用在执行前经过用户人工确认，用户在确认界面仅修改了以下参数：%s。"
                    + "其余参数保持你原始生成的值不变。该修改代表用户的最新意图，"
                    + "优先级高于对话中更早的表述；请基于本结果直接继续，"
                    + "不要按用户更早的说法重试本工具或\"纠正\"参数。";

    /** diff 缺失时的兜底文案（旧暂停态数据等异常场景） */
    private static final String USER_EDITED_NOTE_FALLBACK =
            "\n\n[系统提示] 本次调用的参数在执行前经过用户人工确认与修改，本次实际执行的入参即用户的最新意图，"
                    + "优先级高于对话中更早的表述；请基于本结果直接继续，不要按用户更早的说法重试本工具或\"纠正\"参数。";

    /** 改参调用（记忆改写时打的 metadata 标记）的结果尾部追加用户改参提示；未改参调用原样返回。 */
    private ToolResultBlock appendUserEditedNote(ToolUseBlock toolCall, ToolResultBlock result) {
        if (toolCall == null || result == null
                || !Boolean.TRUE.equals(toolCall.getMetadata().get(EditedInputApplier.META_USER_EDITED_INPUT))) {
            return result;
        }
        Object diff = toolCall.getMetadata().get(EditedInputApplier.META_USER_EDITED_DIFF);
        String note = diff instanceof String s && !s.isBlank()
                ? String.format(USER_EDITED_NOTE_TEMPLATE, s)
                : USER_EDITED_NOTE_FALLBACK;
        List<ContentBlock> output = new java.util.ArrayList<>(
                result.getOutput() == null ? List.of() : result.getOutput());
        output.add(TextBlock.builder().text(note).build());
        return ToolResultBlock.builder()
                .id(result.getId())
                .name(result.getName())
                .output(output)
                .metadata(result.getMetadata())
                .build();
    }

    // ==================== Batch Tool Execution ====================

    /**
     * Execute multiple tool calls with concurrency control, timeout, and retry.
     *
     * @param toolCalls List of tool calls to execute
     * @param parallel Whether to execute in parallel
     * @param executionConfig Execution configuration
     * @param agent The agent making the calls (may be null)
     * @param agentContext The agent-level context (may be null)
     * @return Mono containing list of results
     */
    Mono<List<ToolResultBlock>> executeAll(
            List<ToolUseBlock> toolCalls,
            boolean parallel,
            ExecutionConfig executionConfig,
            Agent agent,
            ToolExecutionContext agentContext) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Mono.just(List.of());
        }

        logger.debug("Executing {} tool calls (parallel={})", toolCalls.size(), parallel);

        // Map each tool call to an execution Mono
        List<Mono<ToolResultBlock>> monos =
                toolCalls.stream()
                        .map(
                                toolCall ->
                                        executeWithInfrastructure(
                                                toolCall, executionConfig, agent, agentContext))
                        .toList();

        // Parallel or sequential execution
        if (parallel) {
            return Flux.mergeSequential(monos).collectList();
        }
        return Flux.concat(monos).collectList();
    }

    /**
     * Execute a single tool call with infrastructure (scheduling, timeout, retry).
     */
    private Mono<ToolResultBlock> executeWithInfrastructure(
            ToolUseBlock toolCall,
            ExecutionConfig executionConfig,
            Agent agent,
            ToolExecutionContext agentContext) {
        // Build tool call parameter
        ToolCallParam param =
                ToolCallParam.builder()
                        .toolUseBlock(toolCall)
                        .agent(agent)
                        .context(agentContext)
                        .build();

        // Get core execution
        Mono<ToolResultBlock> execution = execute(param);

        // Apply infrastructure layers
        execution = applyScheduling(execution);
        execution = applyTimeout(execution, executionConfig, toolCall);
        execution = applyRetry(execution, executionConfig, toolCall);
        execution = applyShutdownGuard(execution);

        // 单工具真实计时：订阅时刻=该工具真正开始执行（串行 concat 下前序完成后才订阅本工具，
        // 排队等待不计入）；compareAndSet 保证 retry 重订阅不重置起点。
        // 批级 collectList 会抹掉单工具完成时刻，这里是全链路唯一能测到真实起止的位置
        AtomicLong startedAt = new AtomicLong(0);

        // Add tool metadata and error handling
        return execution
                .doOnSubscribe(s -> {
                    if (startedAt.compareAndSet(0, System.currentTimeMillis())) {
                        registerToolProgress(toolCall);
                        emitToolStarted(toolCall);
                    }
                })
                .map(result -> result.withIdAndName(toolCall.getId(), toolCall.getName()))
                .doOnNext(result -> emitToolFinished(toolCall, result, startedAt.get()))
                .onErrorResume(
                        e -> {
                            logger.warn("Tool call failed: {}", toolCall.getName(), e);
                            String errorMsg = ExceptionUtils.getErrorMessage(e);
                            return Mono.just(
                                    ToolResultBlock.error("Tool execution failed: " + errorMsg));
                        })
                .doFinally(signalType -> ToolProgressBridge.unregister(toolCall.getId()));
    }

    /** 工作流内部进度复用工具 chunk 通道，保持并行调用时按 toolUseId 精确归属。 */
    private void registerToolProgress(ToolUseBlock toolCall) {
        BiConsumer<ToolUseBlock, ToolResultBlock> callback = getEffectiveChunkCallback();
        ToolProgressBridge.register(toolCall.getId(), progress -> {
            // 即使当前调用没有流式 callback，也继续由 ToolProgressBridge 归档阶段，
            // 供工作流完成时生成持久化模型尝试记录。
            if (callback == null) {
                return;
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tool_progress", true);
            metadata.put("phase", progress.getPhase());
            metadata.put("message", progress.getMessage());
            if (progress.getAttempt() != null) {
                metadata.put("attempt", progress.getAttempt());
            }
            if (progress.getMaxAttempts() != null) {
                metadata.put("max_attempts", progress.getMaxAttempts());
            }
            if (progress.getDetail() != null && !progress.getDetail().isBlank()) {
                metadata.put("detail", progress.getDetail());
            }
            callback.accept(
                    toolCall,
                    new ToolResultBlock(
                            toolCall.getId(), toolCall.getName(), List.of(), metadata));
        });
    }

    /** 工具真正订阅执行时即时通知；模型声明调用不等于获得执行调度。 */
    private void emitToolStarted(ToolUseBlock toolCall) {
        BiConsumer<ToolUseBlock, ToolResultBlock> callback = getEffectiveChunkCallback();
        if (callback == null) {
            return;
        }
        callback.accept(
                toolCall,
                new ToolResultBlock(
                        toolCall.getId(),
                        toolCall.getName(),
                        List.of(),
                        Map.of("tool_started", true)));
    }

    /**
     * 单工具完成即时通知：真实耗时喂入权威耗时表（落库与 TOOL_ELAPSED 下发同源取用），
     * 并经 chunk 通道（与 SubAgentTool 冒泡同款）发 tool_finished 标记——AguiAgentAdapter
     * 转 Custom(TOOL_FINISHED) 实时下发，前端即时翻转完成态，不再等整批 collectList。
     * HITL 挂起结果不算完成（工具在等人，不是跑完了）；无 callback 时静默降级回批量行为。
     */
    private void emitToolFinished(ToolUseBlock toolCall, ToolResultBlock result, long startedAt) {
        if (result.isSuspended() || startedAt <= 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        ChatLogHook.offerToolElapsed(toolCall.getId(), elapsed);
        BiConsumer<ToolUseBlock, ToolResultBlock> callback = getEffectiveChunkCallback();
        if (callback == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tool_finished", true);
        metadata.put("tool_elapsed", elapsed);
        Object workflowProcess = result.getMetadata().get(WorkflowProcessSnapshot.METADATA_KEY);
        if (workflowProcess != null) {
            metadata.put(WorkflowProcessSnapshot.METADATA_KEY, workflowProcess);
        }
        // 完成事件携带真实输出：前端可在同批其他工具尚未结束时立即展示响应，
        // 最终 TOOL_RESULT 仍按原链路进入消息/落库，二者使用同一份 output，避免口径漂移。
        callback.accept(
                toolCall,
                new ToolResultBlock(
                        toolCall.getId(), toolCall.getName(), result.getOutput(), metadata));
    }

    // ==================== Infrastructure Methods ====================

    private Mono<ToolResultBlock> applyScheduling(Mono<ToolResultBlock> execution) {
        if (executorService == null) {
            return execution.subscribeOn(Schedulers.boundedElastic());
        }
        return execution.subscribeOn(Schedulers.fromExecutor(executorService));
    }

    private Mono<ToolResultBlock> applyTimeout(
            Mono<ToolResultBlock> execution, ExecutionConfig config, ToolUseBlock toolCall) {
        if (config == null || config.getTimeout() == null) {
            return execution;
        }

        Duration timeout = config.getTimeout();
        logger.debug("Applied timeout: {} for tool: {}", timeout, toolCall.getName());

        return execution.timeout(
                timeout,
                Mono.error(new RuntimeException("Tool execution timeout after " + timeout)));
    }

    private Mono<ToolResultBlock> applyRetry(
            Mono<ToolResultBlock> execution, ExecutionConfig config, ToolUseBlock toolCall) {
        if (config == null || config.getMaxAttempts() == null || config.getMaxAttempts() <= 1) {
            return execution;
        }

        Integer maxAttempts = config.getMaxAttempts();
        Duration initialBackoff =
                config.getInitialBackoff() != null
                        ? config.getInitialBackoff()
                        : Duration.ofSeconds(1);
        Duration maxBackoff =
                config.getMaxBackoff() != null ? config.getMaxBackoff() : Duration.ofSeconds(10);
        Predicate<Throwable> retryOn =
                config.getRetryOn() != null ? config.getRetryOn() : error -> true;

        Retry retrySpec =
                Retry.backoff(maxAttempts - 1, initialBackoff)
                        .maxBackoff(maxBackoff)
                        .jitter(0.5)
                        .filter(retryOn)
                        .doBeforeRetry(
                                signal ->
                                        logger.warn(
                                                "Retrying tool call (attempt {}/{}) due to: {}",
                                                signal.totalRetriesInARow() + 1,
                                                maxAttempts - 1,
                                                signal.failure().getMessage(),
                                                signal.failure()));

        logger.debug(
                "Applied retry config: maxAttempts={} for tool: {}",
                maxAttempts,
                toolCall.getName());

        return execution.retryWhen(retrySpec);
    }

    /**
     * Race tool execution against the global shutdown timeout signal.
     * When the signal fires, the tool Mono is cancelled and an error is emitted,
     * which flows through {@code onErrorResume} into a normal {@code ToolResultBlock.error}.
     */
    private Mono<ToolResultBlock> applyShutdownGuard(Mono<ToolResultBlock> execution) {
        Mono<ToolResultBlock> shutdownGuard =
                GracefulShutdownManager.getInstance()
                        .getShutdownTimeoutSignal()
                        .then(
                                Mono.error(
                                        new RuntimeException(
                                                "Tool execution timeout due to system graceful"
                                                        + " shutdown.")));
        return Mono.firstWithSignal(execution, shutdownGuard);
    }
}

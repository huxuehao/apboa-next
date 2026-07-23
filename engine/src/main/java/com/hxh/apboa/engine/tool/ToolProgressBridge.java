package com.hxh.apboa.engine.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流工具内部进度桥。
 *
 * <p>外层 ToolExecutor 按 toolUseId 注册监听器；WorkflowTool 把同一个 id 绑定到实际执行线程，
 * 工作流内部节点即可把“等待模型 / 生成中 / 重试”等阶段原路推回该工具的 AG-UI 流。
 * 监听器只覆盖单次工具订阅，并由 ToolExecutor 的 doFinally 兜底注销，避免跨会话泄漏。
 */
@Slf4j
public final class ToolProgressBridge {

    private static final Map<String, Consumer<Progress>> LISTENERS = new ConcurrentHashMap<>();
    /** 单次工具调用的阶段归档；完成时生成 workflowProcess，doFinally 注销时一并清理。 */
    private static final Map<String, List<Progress>> HISTORY = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> CURRENT_TOOL_USE_ID = new ThreadLocal<>();

    private ToolProgressBridge() {}

    public static void register(String toolUseId, Consumer<Progress> listener) {
        if (toolUseId != null && !toolUseId.isBlank() && listener != null) {
            LISTENERS.put(toolUseId, listener);
            HISTORY.put(toolUseId, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    public static void unregister(String toolUseId) {
        if (toolUseId != null) {
            LISTENERS.remove(toolUseId);
            HISTORY.remove(toolUseId);
        }
    }

    /**
     * 获取当前工具已发生阶段的稳定副本，用于工具完成时生成可持久化流程快照。
     */
    public static List<Progress> snapshot(String toolUseId) {
        List<Progress> history = toolUseId == null ? null : HISTORY.get(toolUseId);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    public static void bindCurrent(String toolUseId) {
        if (toolUseId != null && !toolUseId.isBlank()) {
            CURRENT_TOOL_USE_ID.set(toolUseId);
        }
    }

    public static void clearCurrent() {
        CURRENT_TOOL_USE_ID.remove();
    }

    public static String currentToolUseId() {
        return CURRENT_TOOL_USE_ID.get();
    }

    public static void emitCurrent(Progress progress) {
        emit(currentToolUseId(), progress);
    }

    public static void emit(String toolUseId, Progress progress) {
        if (toolUseId == null || progress == null) {
            return;
        }
        Progress normalized = progress.getOccurredAt() == null
                ? progress.toBuilder().occurredAt(System.currentTimeMillis()).build()
                : progress;
        List<Progress> history = HISTORY.get(toolUseId);
        if (history != null) {
            history.add(normalized);
        }
        Consumer<Progress> listener = LISTENERS.get(toolUseId);
        if (listener == null) {
            return;
        }
        try {
            listener.accept(normalized);
        } catch (Exception e) {
            // 进度展示不能反向影响真实工具执行。
            log.warn("工具进度监听失败 toolUseId={}, phase={}: {}",
                    toolUseId, progress.getPhase(), e.getMessage());
        }
    }

    public static Progress stage(String phase, String message) {
        return Progress.builder().phase(phase).message(message).build();
    }

    @Value
    @Builder(toBuilder = true)
    public static class Progress {
        String phase;
        String message;
        Integer attempt;
        Integer maxAttempts;
        String detail;
        String nodeId;
        String nodeName;
        String nodeInvocationId;
        Integer requestIndex;
        Long occurredAt;
        Long elapsed;
    }
}

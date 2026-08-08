package io.agentscope.spring.boot.agui.mvc;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.Disposable;

/**
 * 描述：管理活跃智能体运行的生命周期，解耦 SSE 连接与 Agent Flux 管道。
 *
 * <p>核心职责：
 * <ul>
 *   <li>缓冲 Flux 事件，供多个 SSE 连接（含重连）消费</li>
 *   <li>SSE 断连不影响 Agent 后台运行</li>
 *   <li>提供显式停止、状态查询、活跃运行列表等管理能力</li>
 * </ul>
 *
 * @author huxuehao
 */
public class RunTracker {

    private static final Logger logger = LoggerFactory.getLogger(RunTracker.class);

    /** 事件缓冲区最大容量 */
    private static final int MAX_BUFFER_SIZE = 10000;

    /** 运行完成后延迟清理时间 */
    private static final long CLEANUP_DELAY_MINUTES = 30;

    private final ConcurrentHashMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final AguiEventEncoder encoder;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "run-tracker-cleanup");
        t.setDaemon(true);
        return t;
    });

    public RunTracker(AguiEventEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * 注册一个活跃运行。
     *
     * @param threadId     会话 ID
     * @param agent        智能体实例（用于中断）
     * @param subscription Flux 订阅（用于 dispose）
     */
    public void registerRun(String threadId, Agent agent, Disposable subscription) {
        synchronized (activeRuns) {
            ActiveRun existing = activeRuns.get(threadId);
            if (existing != null && existing.state != RunState.COMPLETED) {
                throw new IllegalStateException(
                        existing.state == RunState.STOPPING
                                ? "Agent is stopping, please wait for it to finish"
                                : "Agent is still running, please wait for it to finish");
            }
            activeRuns.put(threadId, new ActiveRun(agent, subscription));
        }
        logger.debug("Registered active run for thread {}", threadId);
    }

    /**
     * 更新已有活跃运行的 subscription 引用，不替换 ActiveRun 及 buffer。
     *
     * @param threadId     会话 ID
     * @param subscription Flux 订阅
     */
    public void updateSubscription(String threadId, Disposable subscription) {
        ActiveRun run = activeRuns.get(threadId);
        if (run != null) {
            run.subscription = subscription;
            // 停止请求可能早于 Flux 订阅完成，补发一次中断信号，避免订阅时重置中断标志。
            if (run.state == RunState.STOPPING) {
                interruptAgent(run, threadId);
            }
            logger.debug("Updated subscription for thread {}", threadId);
        }
    }

    /**
     * 推送事件到缓冲区并分发给所有活跃 SSE 连接。
     *
     * @param threadId 会话 ID
     * @param event    AG-UI 事件
     */
    public void pushEvent(String threadId, AguiEvent event) {
        ActiveRun run = activeRuns.get(threadId);
        if (run == null) {
            return;
        }

        // 添加到缓冲区（有容量上限）
        synchronized (run.bufferLock) {
            if (run.eventBuffer.size() >= MAX_BUFFER_SIZE) {
                run.eventBuffer.removeFirst();
            }
            run.eventBuffer.add(event);
        }

        // 推送给所有活跃 emitter（含死连接清理）
        // 注意：CopyOnWriteArrayList 的 iterator 不支持 remove，
        // 必须用 list.remove(Object) 来清理死连接，否则 UnsupportedOperationException 会炸入 Flux 管道
        for (SseEmitter emitter : run.emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .data(encoder.encodeToJson(event), MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                logger.debug("Failed to send SSE to emitter for thread {}: {}", threadId, e.getMessage());
                run.emitters.remove(emitter);
            }
        }
    }

    /**
     * SSE 重连：回放已缓冲的事件并接续实时流。
     *
     * <p>关键修正：先注册 emitter 为 subscriber，再回放 buffer，最后 drain 追赶回放期间新到的事件，
     * 避免事件遗漏（而非重复）。
     *
     * @param threadId 会话 ID
     * @param emitter  新的 SSE 连接
     */
    public void reconnect(String threadId, SseEmitter emitter) {
        ActiveRun run = activeRuns.get(threadId);
        if (run == null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            return;
        }

        // 清理同一 threadId 的旧 SSE 连接
        for (SseEmitter old : run.emitters) {
            try {
                old.complete();
            } catch (Exception ignored) {
            }
        }
        run.emitters.clear();

        // 先注册 emitter：后续新事件会直接推送
        run.emitters.add(emitter);

        // 回放缓冲区事件
        int replayedCount;
        synchronized (run.bufferLock) {
            List<AguiEvent> buffer = new ArrayList<>(run.eventBuffer);
            replayedCount = buffer.size();
            for (AguiEvent event : buffer) {
                sendToEmitter(emitter, event);
            }
        }

        // 追赶：drain 回放期间新到的事件
        synchronized (run.bufferLock) {
            for (int i = replayedCount; i < run.eventBuffer.size(); i++) {
                sendToEmitter(emitter, run.eventBuffer.get(i));
            }
        }

        // 发送回放完成标记事件
        try {
            String caughtUpJson = "{\"type\":\"REPLAY_CAUGHT_UP\",\"threadId\":\"" + threadId + "\"}";
            emitter.send(SseEmitter.event().data(caughtUpJson, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            logger.debug("Failed to send REPLAY_CAUGHT_UP for thread {}: {}", threadId, e.getMessage());
        }

        // 如果已经完成，立即关闭
        if (run.state == RunState.COMPLETED) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            run.emitters.remove(emitter);
        }
    }

    /**
     * 强制停止指定会话的智能体运行。
     *
     * @param threadId 会话 ID
     */
    public void stopRun(String threadId) {
        ActiveRun run = activeRuns.get(threadId);
        if (run == null || run.state == RunState.COMPLETED) {
            return;
        }

        run.state = RunState.STOPPING;
        interruptAgent(run, threadId);
        // 不在这里关闭 SSE 或标记完成，必须等待 Agent 流真正结束后再释放运行占用。
        logger.info("Stop requested for thread {}", threadId);
    }

    private void interruptAgent(ActiveRun run, String threadId) {
        try {
            run.agent.interrupt();
        } catch (Exception e) {
            logger.warn("Error interrupting agent for thread {}: {}", threadId, e.getMessage());
        }
    }

    /**
     * 获取指定会话的运行状态。
     *
     * @param threadId 会话 ID
     * @return 是否正在运行
     */
    public boolean getStatus(String threadId) {
        ActiveRun run = activeRuns.get(threadId);
        return run != null && run.state != RunState.COMPLETED;
    }

    /** 获取运行生命周期状态，停止请求期间仍返回 STOPPING。 */
    public RunState getRunState(String threadId) {
        ActiveRun run = activeRuns.get(threadId);
        return run == null ? RunState.COMPLETED : run.state;
    }

    /**
     * 获取所有活跃运行的 threadId 列表。
     *
     * @return 活跃 threadId 集合
     */
    public Set<String> getActiveRuns() {
        return activeRuns.entrySet().stream()
                .filter(e -> e.getValue().state != RunState.COMPLETED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 标记运行完成，启动延迟清理。
     *
     * @param threadId 会话 ID
     */
    public void markCompleted(String threadId) {
        ActiveRun run = activeRuns.get(threadId);
        if (run == null) {
            return;
        }
        if (run.state == RunState.COMPLETED) {
            return;
        }
        run.state = RunState.COMPLETED;

        // 关闭所有 SSE 连接
        for (SseEmitter emitter : run.emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
        run.emitters.clear();

        // 延迟清理
        cleanupExecutor.schedule(() -> {
            // 只清理原运行记录，避免新运行已经注册后被旧任务误删。
            activeRuns.remove(threadId, run);
            logger.debug("Cleaned up completed run for thread {}", threadId);
        }, CLEANUP_DELAY_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 移除 SSE emitter（断开时调用）。
     *
     * @param threadId 会话 ID
     * @param emitter  要移除的 emitter
     */
    public void removeEmitter(String threadId, SseEmitter emitter) {
        ActiveRun run = activeRuns.get(threadId);
        if (run != null) {
            run.emitters.remove(emitter);
        }
    }

    private void sendToEmitter(SseEmitter emitter, AguiEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .data(encoder.encodeToJson(event), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            logger.debug("Failed to send event to emitter: {}", e.getMessage());
        }
    }

    /**
     * 描述：活跃运行的内部状态。
     */
    private static class ActiveRun {

        final Agent agent;
        Disposable subscription;
        final List<AguiEvent> eventBuffer = new ArrayList<>();
        final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        final Object bufferLock = new Object();
        volatile RunState state = RunState.RUNNING;

        ActiveRun(Agent agent, Disposable subscription) {
            this.agent = agent;
            this.subscription = subscription;
        }
    }

    /** 运行生命周期状态。 */
    public enum RunState {
        RUNNING,
        STOPPING,
        COMPLETED
    }
}

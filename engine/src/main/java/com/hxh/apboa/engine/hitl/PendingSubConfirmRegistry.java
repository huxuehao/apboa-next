package com.hxh.apboa.engine.hitl;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子智能体 HITL 确认「挂起-唤醒」注册表。
 *
 * <p>子智能体（Agent-as-Tool）内的需确认工具触发暂停时，SubAgentTool 把确认请求经事件通道
 * 冒泡到主会话前端，并在此注册挂起等待；用户在主会话 UI 决策后经
 * {@code POST /agui/subagent/resume} 调 {@link #complete} 唤醒，子智能体就地续跑。
 * 决策结构 {@link Decision} 与主流程 resume 的 ResumeDecision 对齐（toolUseId/name/approved）。
 *
 * <p>超时保护：挂起默认 10 分钟（系统属性 {@code apboa.hitl.sub-confirm-timeout-seconds} 可覆盖，
 * 实测可临时调短），超时按「全部拒绝」发出决策，防止主 run 因确认无人处理而永久悬挂。
 *
 * <p>TODO 跨实例局限：注册表为进程内存态（Sinks 不可序列化），挂起与唤醒必须发生在同一
 * runtime 实例；当前单机部署无影响，多实例部署需改造为 Redis pub/sub（决策经频道广播、
 * 持有挂起 sink 的实例消费唤醒）。
 *
 * @author huxuehao
 */
@Slf4j
public final class PendingSubConfirmRegistry {

    private PendingSubConfirmRegistry() {}

    /**
     * 单条工具确认决策，字段语义与主流程 AguiRequestProcessor.ResumeDecision 一致。
     *
     * @param input 用户在确认 UI 中修改后的完整参数；null/空 = 未修改（旧客户端兼容）
     */
    public record Decision(
            String toolUseId, String name, boolean approved, Map<String, Object> input) {

        /** 兼容构造器：未携带修改参数的决策（超时全拒等内部构造路径沿用） */
        public Decision(String toolUseId, String name, boolean approved) {
            this(toolUseId, name, approved, null);
        }
    }

    /**
     * 挂起等待的确认请求元数据（也是 GET /agui/subagent/pending 的返回元素，供刷新重建 UI）。
     *
     * @param subSessionId    子智能体会话 ID（唤醒 key）
     * @param parentThreadId  主会话 ID（刷新恢复按此查询）
     * @param parentToolCallId 主 agent 侧工具调用 ID（前端据此定位工具卡片）
     * @param subagentName    子智能体名称
     * @param pending         待确认工具列表，元素 {toolUseId, name, input}（与主流程 pending 同构）
     */
    public record PendingInfo(
            String subSessionId,
            String parentThreadId,
            String parentToolCallId,
            String subagentName,
            List<Map<String, Object>> pending) {}

    private record Entry(PendingInfo info, Sinks.One<List<Decision>> sink) {}

    private static final Map<String, Entry> WAITING = new ConcurrentHashMap<>();

    /** 挂起超时，默认 600 秒；类加载时读系统属性，调整需重启 runtime */
    private static final Duration TIMEOUT =
            Duration.ofSeconds(Long.getLong("apboa.hitl.sub-confirm-timeout-seconds", 600L));

    /**
     * 注册挂起等待。返回的 Mono 在用户决策（{@link #complete}）或超时（全拒绝）时发出决策列表，
     * 任何终止路径（完成/超时/取消）都会清理注册条目。
     */
    public static Mono<List<Decision>> register(PendingInfo info) {
        Sinks.One<List<Decision>> sink = Sinks.one();
        WAITING.put(info.subSessionId(), new Entry(info, sink));
        log.info("子智能体确认挂起: subSessionId={}, subagent={}, pending={} 项, 超时 {}s",
                info.subSessionId(), info.subagentName(), info.pending().size(), TIMEOUT.toSeconds());
        return sink.asMono()
                .timeout(TIMEOUT, Mono.fromSupplier(() -> {
                    log.warn("子智能体确认等待超时（{}s），按全部拒绝处理: subSessionId={}",
                            TIMEOUT.toSeconds(), info.subSessionId());
                    return rejectAll(info);
                }))
                .doFinally(signal -> WAITING.remove(info.subSessionId()));
    }

    /**
     * 提交决策并唤醒挂起的子智能体。
     *
     * @return true=唤醒成功；false=条目不存在（已超时/已决策/实例重启丢失）或 sink 已终止
     */
    public static boolean complete(String subSessionId, List<Decision> decisions) {
        Entry entry = WAITING.remove(subSessionId);
        if (entry == null) {
            log.warn("子智能体确认唤醒失败，挂起条目不存在（已超时或已决策）: subSessionId={}", subSessionId);
            return false;
        }
        boolean ok = entry.sink().tryEmitValue(decisions == null ? List.of() : decisions).isSuccess();
        if (!ok) {
            log.warn("子智能体确认唤醒失败，sink 已终止: subSessionId={}", subSessionId);
        }
        return ok;
    }

    /** 查询某主会话下所有挂起中的子确认请求（前端刷新/重进会话重建确认 UI 用） */
    public static List<PendingInfo> findByParentThreadId(String parentThreadId) {
        if (parentThreadId == null || parentThreadId.isEmpty()) {
            return List.of();
        }
        return WAITING.values().stream()
                .map(Entry::info)
                .filter(info -> parentThreadId.equals(info.parentThreadId()))
                .toList();
    }

    /** 超时兜底：对 pending 内全部工具生成 approved=false 决策 */
    private static List<Decision> rejectAll(PendingInfo info) {
        List<Decision> decisions = new ArrayList<>();
        for (Map<String, Object> tool : info.pending()) {
            decisions.add(new Decision(
                    String.valueOf(tool.get("toolUseId")),
                    String.valueOf(tool.get("name")),
                    false));
        }
        return decisions;
    }
}

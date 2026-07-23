package com.hxh.apboa.workflow.run.cache;

import com.hxh.apboa.workflow.run.RunWorkflow;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：workflow运行缓存
 * <p>
 * 缓存是 JVM 内静态的，而 console 与 runtime 是两个进程：发布/回滚时 console 侧的
 * remove/set 无法送达 runtime，靠失效通知会让 runtime 一直执行旧版编译产物。
 * 因此条目携带版本号做一致性校验：调用方以库中最新发布版本号（每次执行都查库，
 * 跨进程一致的真相源）取缓存，版本不符视为未命中并按新配置重新编译。
 * 同一 workflow 仅保留一个条目，新版本覆盖旧版本，无需额外淘汰。
 *
 * @author huxuehao
 **/
public class RunWorkflowCache {
    private record Entry(String version, RunWorkflow runWorkflow) {}

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    public static RunWorkflow get(String workflowId, String version) {
        Entry entry = CACHE.get(workflowId);
        return entry != null && Objects.equals(entry.version(), version) ? entry.runWorkflow() : null;
    }

    public static void set(String version, RunWorkflow runWorkflow) {
        CACHE.put(runWorkflow.getWorkflowId(), new Entry(version, runWorkflow));
    }

    public static void remove(String workflowId) {
        CACHE.remove(workflowId);
    }
}

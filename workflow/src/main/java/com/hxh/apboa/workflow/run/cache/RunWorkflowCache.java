package com.hxh.apboa.workflow.run.cache;

import com.hxh.apboa.workflow.run.RunWorkflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：workflow运行缓存
 *
 * @author huxuehao
 **/
public class RunWorkflowCache {
    private static final Map<String, RunWorkflow> CACHE = new ConcurrentHashMap<>();
    public static RunWorkflow get(String workflowId) {
        return CACHE.get(workflowId);
    }
    public static void set(RunWorkflow runWorkflow) {
        CACHE.put(runWorkflow.getWorkflowId(), runWorkflow);
    }
    public static void remove(String workflowId) {
        CACHE.remove(workflowId);
    }
}

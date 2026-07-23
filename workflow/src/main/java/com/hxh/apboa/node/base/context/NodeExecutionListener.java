package com.hxh.apboa.node.base.context;

import com.hxh.apboa.node.base.NodeOutput;

/**
 * 工作流节点执行生命周期监听器。
 *
 * <p>监听器只用于旁路观测，不能影响节点真实执行；调用方可用 invocationId 区分
 * 循环中同一 nodeId 的多次执行。
 */
public interface NodeExecutionListener {

    NodeExecutionListener NOOP = new NodeExecutionListener() {};

    default void onNodeStarted(String invocationId, NodeOutput output) {}

    default void onNodeFinished(String invocationId, NodeOutput output) {}

    static NodeExecutionListener noop() {
        return NOOP;
    }
}

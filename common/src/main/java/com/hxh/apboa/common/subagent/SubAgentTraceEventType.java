package com.hxh.apboa.common.subagent;

/**
 * 描述：子智能体调用的追踪事件类型，用于向客户端和历史记录暴露调用过程中的关键节点。
 *       刻意不映射 AgentScope 内部的 EventType 枚举，以保持稳定和解耦。
 *
 * @author huxuehao
 */
public enum SubAgentTraceEventType {
    STARTED,
    MESSAGE_DELTA,
    MESSAGE_COMPLETED,
    TOOL_STARTED,
    TOOL_ARGUMENTS,
    TOOL_COMPLETED,
    STATUS_CHANGED,
    BLOCKED,
    FAILED,
    FINISHED,
    CANCELLED
}

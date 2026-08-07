package com.hxh.apboa.common.subagent;

/**
 * Stable, project-owned event kinds used to expose a sub-agent invocation to clients and history
 * readers. These values deliberately do not mirror AgentScope's internal EventType enum.
 */
public enum SubAgentTraceEventType {
    STARTED,
    MESSAGE_DELTA,
    MESSAGE_COMPLETED,
    TOOL_STARTED,
    TOOL_ARGUMENTS,
    TOOL_COMPLETED,
    STATUS_CHANGED,
    FAILED,
    FINISHED,
    CANCELLED
}

package com.hxh.apboa.engine.agent;

/** Runtime role used while building an agent. */
public enum AgentExecutionRole {
    ROOT,
    SUBAGENT;

    public boolean isSubAgent() {
        return this == SUBAGENT;
    }
}

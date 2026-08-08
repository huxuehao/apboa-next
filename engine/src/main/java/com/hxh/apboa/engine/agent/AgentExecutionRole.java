package com.hxh.apboa.engine.agent;

/**
 * 描述：智能体构建时的角色标识，用于区分根智能体与子智能体。
 *
 * @author huxuehao
 */
public enum AgentExecutionRole {
    ROOT,
    SUBAGENT;

    public boolean isSubAgent() {
        return this == SUBAGENT;
    }
}

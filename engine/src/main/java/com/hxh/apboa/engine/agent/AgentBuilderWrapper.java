package com.hxh.apboa.engine.agent;

import com.hxh.apboa.common.entity.AgentDefinition;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.agent.A2aAgent;
import lombok.Builder;
import lombok.Data;

/**
 * 描述：Agent Builder包装类
 *
 * @author huxuehao
 **/
@Data
@Builder
public class AgentBuilderWrapper {
    AgentDefinition definition;
    ReActAgent.Builder reactAgentBuilder;
    A2aAgent.Builder a2aAgentBuilder;
}

package com.hxh.apboa.node.agent;

/**
 * 智能体节点执行器。
 *
 * @author huxuehao
 */
public interface AgentNodeExecutor {
    /**
     * 阻塞执行智能体节点。
     *
     * @param request 执行请求
     * @return 执行结果
     */
    AgentNodeResult execute(AgentNodeRequest request);
}

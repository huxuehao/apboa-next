package com.hxh.apboa.workflowbiz.service;

import com.hxh.apboa.common.entity.AgentWorkflow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 智能体工作流关联Service
 *
 * @author huxuehao
 */
public interface AgentWorkflowService extends IService<AgentWorkflow> {
    List<Long> getAgentIds(List<Long> workflowIds);
    List<Long> getWorkflowIds(Long agentDefinitionId);
    Boolean insertAgentWorkflow(Long agentDefinitionId, List<Long> workflowIds);
    Boolean deleteAgentWorkflow(List<Long> agentIds);
    Boolean saveAgentWorkflow(Long agentDefinitionId, List<Long> workflowIds);
}

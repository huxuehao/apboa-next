package com.hxh.apboa.workflowbiz.service.impl;

import com.hxh.apboa.common.entity.AgentWorkflow;
import com.hxh.apboa.workflowbiz.mapper.AgentWorkflowMapper;
import com.hxh.apboa.workflowbiz.service.AgentWorkflowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能体工作流关联Service实现
 *
 * @author huxuehao
 */
@Service
public class AgentWorkflowServiceImpl extends ServiceImpl<AgentWorkflowMapper, AgentWorkflow> implements AgentWorkflowService {

    @Override
    public List<Long> getAgentIds(List<Long> workflowIds) {
        return lambdaQuery()
                .in(AgentWorkflow::getWorkflowId, workflowIds)
                .list()
                .stream()
                .map(AgentWorkflow::getAgentDefinitionId)
                .distinct()
                .toList();
    }

    @Override
    public List<Long> getWorkflowIds(Long agentDefinitionId) {
        return lambdaQuery()
                .eq(AgentWorkflow::getAgentDefinitionId, agentDefinitionId)
                .list()
                .stream()
                .map(AgentWorkflow::getWorkflowId)
                .toList();
    }

    @Override
    public Boolean insertAgentWorkflow(Long agentDefinitionId, List<Long> workflowIds) {
        workflowIds.forEach(workflowId -> {
            save(new AgentWorkflow(null, null, agentDefinitionId, workflowId));
        });

        return true;
    }

    @Override
    public Boolean deleteAgentWorkflow(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return true;
        }
        return lambdaUpdate().in(AgentWorkflow::getAgentDefinitionId, agentIds).remove();
    }

    @Override
    public Boolean deleteByWorkflowIds(List<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return true;
        }
        return lambdaUpdate().in(AgentWorkflow::getWorkflowId, workflowIds).remove();
    }

    @Override
    public Boolean saveAgentWorkflow(Long agentDefinitionId, List<Long> workflowIds) {
        deleteAgentWorkflow(List.of(agentDefinitionId));
        insertAgentWorkflow(agentDefinitionId, workflowIds);

        return Boolean.TRUE;
    }
}

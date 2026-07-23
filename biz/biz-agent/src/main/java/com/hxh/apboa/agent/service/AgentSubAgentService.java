package com.hxh.apboa.agent.service;

import com.hxh.apboa.common.entity.AgentSubAgent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 智能体子智能体关联Service
 *
 * @author huxuehao
 */
public interface AgentSubAgentService extends IService<AgentSubAgent> {
    List<Long> getSubAgentIds(Long agentDefinitionId);
    Boolean insertSubAgent(Long agentDefinitionId, List<Long> subAgentIds);
    Boolean deleteSubAgent(List<Long> agentIds);

    /**
     * 清理"被当作子智能体"侧的关联(sub_agent_id 命中)。
     * deleteSubAgent 只清 parent 侧(保存替换也复用它,语义必须保持);
     * 删除 agent 本体时两侧都要清,否则别的 agent 留下悬空的子智能体引用。
     */
    Boolean deleteBySubAgentIds(List<Long> subAgentIds);

    Boolean saveSubAgent(Long agentDefinitionId, List<Long> subAgentIds);
}

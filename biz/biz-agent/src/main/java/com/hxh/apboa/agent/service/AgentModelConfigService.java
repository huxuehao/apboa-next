package com.hxh.apboa.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.entity.AgentModelConfig;

import java.util.List;
import java.util.Map;

/**
 * 智能体候选对话模型关联Service（默认模型在 agent_definition.model_config_id，本表只存额外候选）
 */
public interface AgentModelConfigService extends IService<AgentModelConfig> {

    /** 查询智能体的额外候选模型 id（不含默认模型，按 sort 升序） */
    List<Long> getModelIds(Long agentDefinitionId);

    /** 查询某候选模型的参数覆盖（无关联行或未配置返回 null=跟随模型默认） */
    JsonNode getParamsOverride(Long agentDefinitionId, Long modelConfigId);

    /** 查询智能体全部候选的参数覆盖（key=modelConfigId 字符串；未配置的候选不含在内） */
    Map<String, JsonNode> getParamsOverrideMap(Long agentDefinitionId);

    /**
     * 全量覆盖保存智能体的额外候选模型（null/空 = 清空）。
     *
     * @param paramsOverrides 各候选的参数覆盖（key=modelConfigId 字符串；缺省/null=跟随模型默认）
     */
    Boolean saveAgentModel(Long agentDefinitionId, List<Long> modelConfigIds, Map<String, JsonNode> paramsOverrides);

    /** 删除智能体的全部候选关联（agent 删除级联用） */
    Boolean deleteAgentModel(List<Long> agentIds);
}

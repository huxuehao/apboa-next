package com.hxh.apboa.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.agent.mapper.AgentModelConfigMapper;
import com.hxh.apboa.agent.service.AgentModelConfigService;
import com.hxh.apboa.common.entity.AgentModelConfig;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体候选对话模型关联Service实现
 */
@Service
public class AgentModelConfigServiceImpl extends ServiceImpl<AgentModelConfigMapper, AgentModelConfig> implements AgentModelConfigService {

    @Override
    public List<Long> getModelIds(Long agentDefinitionId) {
        return lambdaQuery()
                .eq(AgentModelConfig::getAgentDefinitionId, agentDefinitionId)
                .orderByAsc(AgentModelConfig::getSort)
                .list()
                .stream()
                .map(AgentModelConfig::getModelConfigId)
                .toList();
    }

    @Override
    public JsonNode getParamsOverride(Long agentDefinitionId, Long modelConfigId) {
        AgentModelConfig row = lambdaQuery()
                .eq(AgentModelConfig::getAgentDefinitionId, agentDefinitionId)
                .eq(AgentModelConfig::getModelConfigId, modelConfigId)
                .one();
        return row == null ? null : row.getModelParamsOverride();
    }

    @Override
    public Map<String, JsonNode> getParamsOverrideMap(Long agentDefinitionId) {
        Map<String, JsonNode> map = new HashMap<>();
        lambdaQuery()
                .eq(AgentModelConfig::getAgentDefinitionId, agentDefinitionId)
                .list()
                .forEach(row -> {
                    if (row.getModelParamsOverride() != null) {
                        map.put(String.valueOf(row.getModelConfigId()), row.getModelParamsOverride());
                    }
                });
        return map;
    }

    @Override
    public Boolean saveAgentModel(Long agentDefinitionId, List<Long> modelConfigIds, Map<String, JsonNode> paramsOverrides) {
        deleteAgentModel(List.of(agentDefinitionId));
        if (modelConfigIds == null || modelConfigIds.isEmpty()) {
            return Boolean.TRUE;
        }
        for (int i = 0; i < modelConfigIds.size(); i++) {
            Long modelConfigId = modelConfigIds.get(i);
            JsonNode params = paramsOverrides == null ? null : paramsOverrides.get(String.valueOf(modelConfigId));
            save(new AgentModelConfig(null, null, agentDefinitionId, modelConfigId, params, i));
        }
        return Boolean.TRUE;
    }

    @Override
    public Boolean deleteAgentModel(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Boolean.TRUE;
        }
        return lambdaUpdate().in(AgentModelConfig::getAgentDefinitionId, agentIds).remove();
    }
}

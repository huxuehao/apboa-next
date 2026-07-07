package com.hxh.apboa.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.a2a.service.AgentA2aService;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.agent.mapper.IJobInfoMapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.agent.service.AgentSubAgentService;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.AgentType;
import com.hxh.apboa.common.enums.ModelType;
import com.hxh.apboa.common.enums.workflow.WorkflowStatus;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.hook.service.AgentHookService;
import com.hxh.apboa.knowledge.service.AgentKnowledgeBaseService;
import com.hxh.apboa.mcp.service.AgentMcpServerService;
import com.hxh.apboa.model.service.ModelConfigService;
import com.hxh.apboa.params.core.ParamsAdapter;
import com.hxh.apboa.skill.service.AgentSkillPackageService;
import com.hxh.apboa.skill.service.SkillPackageService;
import com.hxh.apboa.studio.service.AgentStudioService;
import com.hxh.apboa.longterm.service.AgentLongTermMemoryService;
import com.hxh.apboa.tool.service.AgentToolService;
import com.hxh.apboa.agent.service.AgentCodeExecutionService;
import com.hxh.apboa.workflowbiz.service.AgentWorkflowService;
import com.hxh.apboa.workflowbiz.service.WorkflowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体定义Service实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class AgentDefinitionServiceImpl extends ServiceImpl<AgentDefinitionMapper, AgentDefinition> implements AgentDefinitionService {
    private final AgentHookService agentHookService;
    private final AgentToolService agentToolService;
    private final ToolService toolService;
    private final AgentMcpServerService agentMcpServerService;
    private final AgentSkillPackageService agentSkillPackageService;
    private final SkillPackageService skillPackageService;
    private final AgentSubAgentService agentSubAgentService;
    private final AgentKnowledgeBaseService agentKnowledgeBaseService;
    private final ModelConfigService modelConfigService;
    private final ParamsAdapter paramsAdapter;
    private final AgentA2aService agentA2aService;
    private final AgentStudioService agentStudioService;
    private final AgentLongTermMemoryService agentLongTermMemoryService;
    private final IJobInfoMapper iJobInfoMapper;
    private final AgentCodeExecutionService agentCodeExecutionService;
    private final AgentWorkflowService agentWorkflowService;
    private final WorkflowService workflowService;
    private final MessagePublisher messagePublisher;

    @Override
    public AgentDefinitionVO agentDefinitionDetail(Long id) {
        AgentDefinition entity = getById(id);
        if (entity == null) {
            throw new RuntimeException("AgentDefinition not found for id: " + id);
        }

        AgentDefinitionVO vo = BeanUtils.copy(entity, AgentDefinitionVO.class);

        vo.setHook(agentHookService.getHookIds(id));
        Long studioConfigId = agentStudioService.getStudioIdByAgentId(id);
        if (studioConfigId != null) {
            vo.setStudioConfigId(studioConfigId);
        }
        Long codeExecutionId = agentCodeExecutionService.getCodeExecutionIdByAgentId(id);
        if (codeExecutionId != null) {
            vo.setCodeExecutionConfigId(codeExecutionId);
        }
        Long longTermMemoryConfigId = agentLongTermMemoryService.getConfigIdByAgentId(id);
        if (longTermMemoryConfigId != null) {
            vo.setLongTermMemoryConfigId(longTermMemoryConfigId);
        }

        if(entity.getAgentType() == AgentType.CUSTOM) {
            vo.setTool(agentToolService.getToolIds(id));
            vo.setMcp(agentMcpServerService.getMcpIds(id));
            vo.setMcpBindings(agentMcpServerService.getBindings(id));
            vo.setSkill(agentSkillPackageService.getSkillPackageIds(id));
            vo.setSubAgent(agentSubAgentService.getSubAgentIds(id));
            vo.setKnowledgeBase(agentKnowledgeBaseService.getKnowledgeIds(id));
            vo.setWorkflow(agentWorkflowService.getWorkflowIds(id));
        } else {
            vo.setAgentA2A(agentA2aService.getA2aConfigByAgentId(id));
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveAgentDefinition(AgentDefinitionVO vo) {
        AgentDefinition agentDefinition = BeanUtils.copy(vo, AgentDefinition.class);
        save(agentDefinition);
        vo.setId(agentDefinition.getId());

        saveSubItems(vo);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateAgentDefinition(AgentDefinitionVO vo) {
        AgentDefinition oldAgent = getById(vo.getId());
        updateById(BeanUtils.copy(vo, AgentDefinition.class));

        // 成立条件：禁用/启用操作
        if (vo.getAgentCode() == null) {
            List<JobInfo> agent = iJobInfoMapper.selectList(
                    new LambdaQueryWrapper<JobInfo>()
                            .eq(JobInfo::getType, "AGENT")
                            .eq(JobInfo::getBizId, vo.getId()));
            if (!agent.isEmpty() && agent.getFirst().isEnabled()) {
                throw new RuntimeException("请先禁用定时任务");
            }
            if (vo.getEnabled()) {
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(vo.getId()));
            } else {
                AgentDefinition agentDefinition = getById(vo.getId());
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_UNREGISTER_CHANNEL, agentDefinition.getAgentCode());
            }

            return true;
        }

        saveSubItems(vo);

        // Agent Code 发生变化，将旧的 Agent 注销
        if (!oldAgent.getAgentCode().equals(vo.getAgentCode())) {
            messagePublisher.publish(RedisChannelTopic.AGENT_UNREGISTER_CHANNEL, oldAgent.getAgentCode());
        }

        messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(vo.getId()));
        return true;
    }

    private void saveSubItems(AgentDefinitionVO vo) {
        agentHookService.saveAgentHook(vo.getId(), vo.getHook());
        if (vo.getAgentType() == AgentType.CUSTOM) {
            agentSubAgentService.saveSubAgent(vo.getId(), vo.getSubAgent());
            agentToolService.saveAgentTool(vo.getId(), vo.getTool());
            agentMcpServerService.saveAgentMcpServer(vo.getId(), vo.getMcp(), vo.getMcpBindings());
            agentSkillPackageService.saveAgentSkillPackage(vo.getId(), vo.getSkill());
            agentKnowledgeBaseService.saveAgentKnowledge(vo.getId(), vo.getKnowledgeBase());
            agentWorkflowService.saveAgentWorkflow(vo.getId(), vo.getWorkflow());
            if (vo.getStudioConfigId() != null) {
                agentStudioService.saveAgentStudio(vo.getId(), List.of(vo.getStudioConfigId()));
            } else {
                agentStudioService.deleteAgentStudio(List.of(vo.getId()));
            }
            if (vo.getCodeExecutionConfigId() != null) {
                agentCodeExecutionService.saveAgentCodeExecution(vo.getId(), List.of(vo.getCodeExecutionConfigId()));
            } else {
                agentCodeExecutionService.deleteAgentCodeExecution(List.of(vo.getId()));
            }
            if (vo.getLongTermMemoryConfigId() != null) {
                agentLongTermMemoryService.saveAgentLongTermMemory(vo.getId(), vo.getLongTermMemoryConfigId());
            } else {
                agentLongTermMemoryService.deleteAgentLongTermMemory(List.of(vo.getId()));
            }
        } else {
            AgentA2A agentA2A = vo.getAgentA2A();
            agentA2A.setAgentDefinitionId(vo.getId());
            agentA2aService.saveA2aConfig(agentA2A);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteAgentDefinition(List<Long> ids) {
        List<JobInfo> agent = iJobInfoMapper.selectList(
                new LambdaQueryWrapper<JobInfo>()
                        .eq(JobInfo::getType, "AGENT")
                        .in(JobInfo::getBizId, ids));
        if (!agent.isEmpty()) {
            throw new RuntimeException("请先解绑定时任务");
        }

        List<AgentDefinition> agents = listByIds(ids);

        removeByIds(ids);
        agentA2aService.deleteA2aConfig(ids);
        agentSubAgentService.deleteSubAgent(ids);
        agentHookService.deleteAgentHook(ids);
        agentToolService.deleteAgentTool(ids);
        agentMcpServerService.deleteAgentMcpServer(ids);
        agentSkillPackageService.deleteAgentSkillPackage(ids);
        agentKnowledgeBaseService.deleteAgentKnowledge(ids);
        agentWorkflowService.deleteAgentWorkflow(ids);
        agentStudioService.deleteAgentStudio(ids);
        agentCodeExecutionService.deleteAgentCodeExecution(ids);
        agentLongTermMemoryService.deleteAgentLongTermMemory(ids);

        for (AgentDefinition agent_ : agents) {
            messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_UNREGISTER_CHANNEL, agent_.getAgentCode());
        }

        return Boolean.TRUE;
    }

    @Override
    public List<Object> usedWithAgent(List<Long> ids) {
        List<Object> names = new ArrayList<>();
        ids.forEach(id -> {
            agentSubAgentService.getSubAgentIds(id).forEach(subAgentId -> {
                AgentDefinition agentDefinition = getById(subAgentId);
                if (agentDefinition != null) {
                    names.add(agentDefinition.getName());
                }
            });
        });

        return names;
    }

    @Override
    public List<String> listTags() {
        return this.lambdaQuery()
                .select(AgentDefinition::getTag)
                .isNotNull(AgentDefinition::getTag)
                .groupBy(AgentDefinition::getTag)
                .list()
                .stream()
                .map(AgentDefinition::getTag)
                .filter(category -> category != null && !category.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> allowFileType(Long id) {
        AgentDefinition agentDefinition = getById(id);
        if (agentDefinition == null || !agentDefinition.getEnabled()) {
            return List.of();
        }

        ModelConfig modelConfig = modelConfigService.getById(agentDefinition.getModelConfigId());
        if (modelConfig == null) {
            return List.of();
        }
        JsonNode modelTypeJ = modelConfig.getModelType();
        if (modelTypeJ == null) {
            return List.of();
        }

        List<String> modelType = parseModelType(modelTypeJ);
        List<String> allTypes = new ArrayList<>();
        // 现有多模态类型（受模型能力控制）
        if (modelType.contains(ModelType.IMAGE.name())) {
            allTypes.add(paramsAdapter.getValue("ALLOW_IMAGE_FILE_TYPE"));
        }
        if (modelType.contains(ModelType.AUDIO.name())) {
            allTypes.add(paramsAdapter.getValue("ALLOW_AUDIO_FILE_TYPE"));
        }
        if (modelType.contains(ModelType.VIDEO.name())) {
            allTypes.add(paramsAdapter.getValue("ALLOW_VIDEO_FILE_TYPE"));
        }

        // 无条件追加文档类型（框架能力，与模型多模态能力无关）— 必须在空判断之前
        allTypes.add(paramsAdapter.getValue("ALLOW_DOC_FILE_TYPE"));
        allTypes.add(paramsAdapter.getValue("ALLOW_EXCEL_FILE_TYPE"));
        allTypes.add(paramsAdapter.getValue("ALLOW_PPT_FILE_TYPE"));

        if (allTypes.isEmpty()) {
            return List.of();
        }

        String join = String.join(",", allTypes);
        return List.of(join.split(","));
    }

    @Override
    public List<ToolConfig> getEnabledToolsOfAgent(Long agentId) {
        List<Long> toolIds = agentToolService.getToolIds(agentId);
        if (!toolIds.isEmpty()) {
            return toolService.list(
                    new LambdaQueryWrapper<ToolConfig>()
                            .select(ToolConfig::getId, ToolConfig::getName, ToolConfig::getToolId, ToolConfig::getDescription)
                            .eq(ToolConfig::getEnabled, true)
                            .in(ToolConfig::getId, toolIds));
        }
        return List.of();
    }

    @Override
    public List<SkillPackage> getEnabledSkillsOfAgent(Long agentId) {
        List<Long> skillPackageIds = agentSkillPackageService.getSkillPackageIds(agentId);
        if (!skillPackageIds.isEmpty()) {
            return skillPackageService.list(
                    new LambdaQueryWrapper<SkillPackage>()
                            .select(SkillPackage::getId, SkillPackage::getName, SkillPackage::getDescription)
                            .eq(SkillPackage::getEnabled, true)
                            .in(SkillPackage::getId, skillPackageIds));
        }
        return Collections.emptyList();
    }

    @Override
    public List<Workflow> getEnabledWorkflowsOfAgent(Long agentId) {
        List<Long> workflowIds = agentWorkflowService.getWorkflowIds(agentId);
        if (!workflowIds.isEmpty()) {
            return workflowService.list(
                    new LambdaQueryWrapper<Workflow>()
                            .select(Workflow::getId, Workflow::getName, Workflow::getRemark)
                            .eq(Workflow::getEnabled, true)
                            .eq(Workflow::getStatus, WorkflowStatus.PUBLISHED)
                            .in(Workflow::getId, workflowIds));
        }
        return Collections.emptyList();
    }

    private List<String> parseModelType(JsonNode modelTypeJ) {
        try {
            return (List<String>)JsonUtils.parse(modelTypeJ.toString(), List.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}

package com.hxh.apboa.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.a2a.service.AgentA2aService;
import com.hxh.apboa.agent.mapper.AgentChatKeyMapper;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.agent.mapper.IJobInfoMapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.agent.service.AgentModelConfigService;
import com.hxh.apboa.agent.service.AgentSubAgentService;
import com.hxh.apboa.agent.service.CodeExecutionConfigService;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.AgentType;
import com.hxh.apboa.common.enums.ModelCategory;
import com.hxh.apboa.common.enums.ModelType;
import com.hxh.apboa.common.enums.workflow.WorkflowStatus;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.hook.service.AgentHookService;
import com.hxh.apboa.hook.service.HookConfigService;
import com.hxh.apboa.knowledge.service.AgentKnowledgeBaseService;
import com.hxh.apboa.knowledge.service.KnowledgeBaseConfigService;
import com.hxh.apboa.mcp.service.AgentMcpServerService;
import com.hxh.apboa.model.service.ModelConfigService;
import com.hxh.apboa.params.core.ParamsAdapter;
import com.hxh.apboa.prompt.service.SystemPromptTemplateService;
import com.hxh.apboa.sensitive.service.SensitiveWordConfigService;
import com.hxh.apboa.skill.service.AgentSkillPackageService;
import com.hxh.apboa.skill.service.SkillPackageService;
import com.hxh.apboa.studio.service.AgentStudioService;
import com.hxh.apboa.studio.service.StudioConfigService;
import com.hxh.apboa.longterm.service.AgentLongTermMemoryService;
import com.hxh.apboa.longterm.service.LongTermMemoryConfigService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.hxh.apboa.mcp.service.McpServerService;
import com.hxh.apboa.mcp.service.McpToolService;
import com.hxh.apboa.common.enums.McpToolExposureMode;
import com.hxh.apboa.common.vo.McpServerToolsVO;
import com.hxh.apboa.common.vo.AgentMcpBindingVO;

/**
 * 智能体定义Service实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class AgentDefinitionServiceImpl extends ServiceImpl<AgentDefinitionMapper, AgentDefinition> implements AgentDefinitionService {
    private final AgentHookService agentHookService;
    private final AgentModelConfigService agentModelConfigService;
    private final AgentToolService agentToolService;
    private final ToolService toolService;
    private final AgentMcpServerService agentMcpServerService;
    private final McpServerService mcpServerService;
    private final McpToolService mcpToolService;
    private final AgentSkillPackageService agentSkillPackageService;
    private final SkillPackageService skillPackageService;
    private final AgentSubAgentService agentSubAgentService;
    private final AgentKnowledgeBaseService agentKnowledgeBaseService;
    private final ModelConfigService modelConfigService;
    // 悬空引用读取端过滤所需的目标表 service(biz-agent pom 已依赖全部相关模块,均无反向依赖、不成环)
    private final HookConfigService hookConfigService;
    private final KnowledgeBaseConfigService knowledgeBaseConfigService;
    private final StudioConfigService studioConfigService;
    private final CodeExecutionConfigService codeExecutionConfigService;
    private final LongTermMemoryConfigService longTermMemoryConfigService;
    private final SystemPromptTemplateService systemPromptTemplateService;
    private final SensitiveWordConfigService sensitiveWordConfigService;
    // 删 agent 清 chat_key 用 mapper:AgentChatKeyServiceImpl 反向依赖本 service,注入 service 会成环
    private final AgentChatKeyMapper agentChatKeyMapper;
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

        // ===== 悬空引用读取端收口 =====
        // 关联/引用的目标行可能已不存在(内置同步清理、历史删除路径未级联等),
        // detail 是配置读取的单一收口:这里统一过滤,保证吐给前端的每个 id 都能查到详情,
        // 架构页等"逐 id 拉详情"的消费方不再撞"XX不存在"。
        vo.setHook(retainExisting(agentHookService.getHookIds(id),
                ids -> hookConfigService.listByIds(ids).stream().map(HookConfig::getId).collect(Collectors.toSet())));
        vo.setModels(retainExisting(agentModelConfigService.getModelIds(id),
                ids -> modelConfigService.listByIds(ids).stream().map(ModelConfig::getId).collect(Collectors.toSet())));
        vo.setModelsParamsOverride(agentModelConfigService.getParamsOverrideMap(id));
        Long studioConfigId = agentStudioService.getStudioIdByAgentId(id);
        if (studioConfigId != null && studioConfigService.getById(studioConfigId) != null) {
            vo.setStudioConfigId(studioConfigId);
        }
        Long codeExecutionId = agentCodeExecutionService.getCodeExecutionIdByAgentId(id);
        if (codeExecutionId != null && codeExecutionConfigService.getById(codeExecutionId) != null) {
            vo.setCodeExecutionConfigId(codeExecutionId);
        }
        Long longTermMemoryConfigId = agentLongTermMemoryService.getConfigIdByAgentId(id);
        if (longTermMemoryConfigId != null && longTermMemoryConfigService.getById(longTermMemoryConfigId) != null) {
            vo.setLongTermMemoryConfigId(longTermMemoryConfigId);
        }

        if(entity.getAgentType() == AgentType.CUSTOM) {
            vo.setTool(retainExisting(agentToolService.getToolIds(id),
                    ids -> toolService.listByIds(ids).stream().map(ToolConfig::getId).collect(Collectors.toSet())));
            List<Long> mcpIds = retainExisting(agentMcpServerService.getMcpIds(id),
                    ids -> mcpServerService.listByIds(ids).stream().map(McpServer::getId).collect(Collectors.toSet()));
            vo.setMcp(mcpIds);
            // bindings 与 mcp ids 同源 agent_mcp_servers,按过滤后的 server 集合同步收敛
            // (bindings 内的 mcpToolIds 在 getBindings 里已按 mcp_tool 表回查,天然无悬空)
            Set<Long> validMcpIds = new HashSet<>(mcpIds);
            vo.setMcpBindings(agentMcpServerService.getBindings(id).stream()
                    .filter(binding -> validMcpIds.contains(binding.getMcpServerId()))
                    .toList());
            vo.setSkill(retainExisting(agentSkillPackageService.getSkillPackageIds(id),
                    ids -> skillPackageService.listByIds(ids).stream().map(SkillPackage::getId).collect(Collectors.toSet())));
            vo.setSubAgent(retainExisting(agentSubAgentService.getSubAgentIds(id),
                    ids -> listByIds(ids).stream().map(AgentDefinition::getId).collect(Collectors.toSet())));
            vo.setKnowledgeBase(retainExisting(agentKnowledgeBaseService.getKnowledgeIds(id),
                    ids -> knowledgeBaseConfigService.listByIds(ids).stream().map(KnowledgeBaseConfig::getId).collect(Collectors.toSet())));
            vo.setWorkflow(retainExisting(agentWorkflowService.getWorkflowIds(id),
                    ids -> workflowService.listByIds(ids).stream().map(Workflow::getId).collect(Collectors.toSet())));
        } else {
            vo.setAgentA2A(agentA2aService.getA2aConfigByAgentId(id));
        }

        // 直连列(模型/提示词/敏感词):目标被删后列值可能悬空(历史数据),VO 层置 null,
        // 前端按"未配置"渲染引导重新绑定;库内列值由各删除路径的置空逻辑负责,这里只兜读取
        Set<Long> modelIds = new HashSet<>();
        if (vo.getModelConfigId() != null) { modelIds.add(vo.getModelConfigId()); }
        if (vo.getAsrModelConfigId() != null) { modelIds.add(vo.getAsrModelConfigId()); }
        if (vo.getTtsModelConfigId() != null) { modelIds.add(vo.getTtsModelConfigId()); }
        if (!modelIds.isEmpty()) {
            Set<Long> existingModelIds = modelConfigService.listByIds(modelIds).stream()
                    .map(ModelConfig::getId).collect(Collectors.toSet());
            if (vo.getModelConfigId() != null && !existingModelIds.contains(vo.getModelConfigId())) {
                vo.setModelConfigId(null);
            }
            if (vo.getAsrModelConfigId() != null && !existingModelIds.contains(vo.getAsrModelConfigId())) {
                vo.setAsrModelConfigId(null);
            }
            if (vo.getTtsModelConfigId() != null && !existingModelIds.contains(vo.getTtsModelConfigId())) {
                vo.setTtsModelConfigId(null);
            }
        }
        if (vo.getSystemPromptTemplateId() != null
                && systemPromptTemplateService.getById(vo.getSystemPromptTemplateId()) == null) {
            vo.setSystemPromptTemplateId(null);
        }
        if (vo.getSensitiveWordConfigId() != null
                && sensitiveWordConfigService.getById(vo.getSensitiveWordConfigId()) == null) {
            vo.setSensitiveWordConfigId(null);
        }

        return vo;
    }

    /**
     * 悬空引用过滤:保留目标表中仍存在的 id(顺序不变)。
     * 关联表历史上存在未级联清理的删除路径(如内置同步只删主表),读取端在此统一兜底。
     */
    private List<Long> retainExisting(List<Long> ids, Function<List<Long>, Set<Long>> existingIdsLoader) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }
        Set<Long> existing = existingIdsLoader.apply(ids);
        return ids.stream().filter(existing::contains).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveAgentDefinition(AgentDefinitionVO vo) {
        validateAsrModelConfig(vo.getAsrModelConfigId());
        validateTtsModelConfig(vo.getTtsModelConfigId());
        validateCandidateModels(vo.getModels());
        AgentDefinition agentDefinition = BeanUtils.copy(vo, AgentDefinition.class);
        save(agentDefinition);
        vo.setId(agentDefinition.getId());

        saveSubItems(vo);

        // 新建后注册到 runtime 的内存 registry（对齐 update 行为）。
        // 不发的话新 agent 在 runtime 重启前不可对话（Agent not found: {agentCode}）。
        messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL,
                String.valueOf(vo.getId()));

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateAgentDefinition(AgentDefinitionVO vo) {
        validateAsrModelConfig(vo.getAsrModelConfigId());
        validateTtsModelConfig(vo.getTtsModelConfigId());
        validateCandidateModels(vo.getModels());
        AgentDefinition oldAgent = getById(vo.getId());
        updateById(BeanUtils.copy(vo, AgentDefinition.class));

        // 成立条件：禁用/启用操作
        if (vo.getAgentCode() == null) {
            List<JobInfo> agent = iJobInfoMapper.selectList(
                    new LambdaQueryWrapper<JobInfo>()
                            .eq(JobInfo::getType, "AGENT")
                            .eq(JobInfo::getBizId, vo.getId()));
            if (!agent.isEmpty() && agent.getFirst().getEnabled()) {
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

        // updateById 忽略 null 字段，ASR/TTS 绑定是可清空字段，照 updateAvatar 先例显式 set（null=解绑）
        lambdaUpdate()
                .eq(AgentDefinition::getId, vo.getId())
                .set(AgentDefinition::getAsrModelConfigId, vo.getAsrModelConfigId())
                .set(AgentDefinition::getTtsModelConfigId, vo.getTtsModelConfigId())
                .update();

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
            agentModelConfigService.saveAgentModel(vo.getId(), vo.getModels(), vo.getModelsParamsOverride());
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
        // 被删 agent 也可能被别的 agent 挂作子智能体(sub_agent_id 侧),不清会留悬空引用
        agentSubAgentService.deleteBySubAgentIds(ids);
        agentHookService.deleteAgentHook(ids);
        agentModelConfigService.deleteAgentModel(ids);
        agentToolService.deleteAgentTool(ids);
        agentMcpServerService.deleteAgentMcpServer(ids);
        agentSkillPackageService.deleteAgentSkillPackage(ids);
        agentKnowledgeBaseService.deleteAgentKnowledge(ids);
        agentWorkflowService.deleteAgentWorkflow(ids);
        agentStudioService.deleteAgentStudio(ids);
        agentCodeExecutionService.deleteAgentCodeExecution(ids);
        agentLongTermMemoryService.deleteAgentLongTermMemory(ids);
        // 外置对话入口按 agent_code 关联,不清会留下指向已删 agent 的 chatKey 分享链接
        List<String> agentCodes = agents.stream()
                .map(AgentDefinition::getAgentCode)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!agentCodes.isEmpty()) {
            agentChatKeyMapper.delete(new LambdaQueryWrapper<AgentChatKey>().in(AgentChatKey::getAgentCode, agentCodes));
        }

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
                            .select(SkillPackage::getId, SkillPackage::getName, SkillPackage::getDescription, SkillPackage::getAlias)
                            .eq(SkillPackage::getEnabled, true)
                            .in(SkillPackage::getId, skillPackageIds));
        }
        return Collections.emptyList();
    }

    @Override
    public List<McpServerToolsVO> getEnabledMcpOfAgent(Long agentId) {
        List<McpServerToolsVO> result = new ArrayList<>();
        for (AgentMcpBindingVO binding : agentMcpServerService.getBindings(agentId)) {
            McpServer server = mcpServerService.getById(binding.getMcpServerId());
            if (server == null) {
                continue;
            }
            List<McpTool> tools = mcpToolService.listRuntimeTools(server.getId());
            if (binding.getExposureMode() == McpToolExposureMode.SELECTED_ONLY) {
                Set<Long> selected = new HashSet<>(binding.getMcpToolIds() == null
                        ? List.of() : binding.getMcpToolIds());
                tools = tools.stream().filter(t -> selected.contains(t.getId())).toList();
            }
            List<McpServerToolsVO.McpToolBrief> briefs = tools.stream()
                    .filter(t -> !Boolean.TRUE.equals(t.getMissing()))
                    .map(t -> new McpServerToolsVO.McpToolBrief(t.getToolName(), t.getDescription()))
                    .toList();
            if (!briefs.isEmpty()) {
                result.add(new McpServerToolsVO(server.getId(), server.getName(), briefs));
            }
        }
        return result;
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

    @Override
    public List<AgentDefinition> getEnabledSubAgentsOfAgent(Long agentId) {
        List<Long> subAgentIds = agentSubAgentService.getSubAgentIds(agentId);
        if (subAgentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .select(AgentDefinition::getId, AgentDefinition::getAgentCode,
                        AgentDefinition::getName, AgentDefinition::getDescription)
                .eq(AgentDefinition::getEnabled, true)
                .in(AgentDefinition::getId, subAgentIds)
                .list();
    }

    @Override
    public String getAvatar(Long id) {
        // 只取 avatar 单列，避免加载整行（含 JSON 大字段）
        AgentDefinition agent = lambdaQuery()
                .select(AgentDefinition::getAvatar)
                .eq(AgentDefinition::getId, id)
                .one();
        return agent == null ? null : agent.getAvatar();
    }

    @Override
    public Boolean updateAvatar(Long id, String avatar) {
        // 显式 set：avatar 为 null/空时清除头像
        return lambdaUpdate()
                .eq(AgentDefinition::getId, id)
                .set(AgentDefinition::getAvatar, (avatar == null || avatar.isEmpty()) ? null : avatar)
                .update();
    }

    /**
     * 校验语音识别模型绑定：必须指向存在、启用、且用途为 ASR 的模型配置
     */
    /**
     * 校验额外候选对话模型：每个都必须指向存在、启用、且用途为 LLM 的模型配置
     */
    private void validateCandidateModels(List<Long> modelConfigIds) {
        if (modelConfigIds == null || modelConfigIds.isEmpty()) {
            return;
        }
        for (Long id : modelConfigIds) {
            ModelConfig modelConfig = modelConfigService.getById(id);
            if (modelConfig == null || !modelConfig.getEnabled()) {
                throw new RuntimeException("候选对话模型不存在或已禁用");
            }
            if (modelConfig.getCategory() != ModelCategory.LLM) {
                throw new RuntimeException("候选模型的用途不是对话生成");
            }
        }
    }

    private void validateAsrModelConfig(Long asrModelConfigId) {
        if (asrModelConfigId == null) {
            return;
        }
        ModelConfig modelConfig = modelConfigService.getById(asrModelConfigId);
        if (modelConfig == null || !modelConfig.getEnabled()) {
            throw new RuntimeException("语音识别模型不存在或已禁用");
        }
        if (modelConfig.getCategory() != ModelCategory.ASR) {
            throw new RuntimeException("所选模型的用途不是语音识别");
        }
    }

    /**
     * 校验语音合成模型绑定：必须指向存在、启用、且用途为 TTS 的模型配置
     */
    private void validateTtsModelConfig(Long ttsModelConfigId) {
        if (ttsModelConfigId == null) {
            return;
        }
        ModelConfig modelConfig = modelConfigService.getById(ttsModelConfigId);
        if (modelConfig == null || !modelConfig.getEnabled()) {
            throw new RuntimeException("语音合成模型不存在或已禁用");
        }
        if (modelConfig.getCategory() != ModelCategory.TTS) {
            throw new RuntimeException("所选模型的用途不是语音合成");
        }
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

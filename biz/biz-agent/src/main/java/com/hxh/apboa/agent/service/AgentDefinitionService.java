package com.hxh.apboa.agent.service;

import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.hxh.apboa.common.vo.McpServerToolsVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 智能体定义Service
 *
 * @author huxuehao
 */
public interface AgentDefinitionService extends IService<AgentDefinition> {
    AgentDefinitionVO agentDefinitionDetail(Long id);
    Boolean saveAgentDefinition(AgentDefinitionVO agentDefinition);
    Boolean updateAgentDefinition(AgentDefinitionVO agentDefinition);
    Boolean deleteAgentDefinition(List<Long> ids);
    List<Object> usedWithAgent(List<Long> ids);
    /**
     * 获取所有Tag
     *
     * @return Tag列表
     */
    List<String> listTags();
    List<String> allowFileType(Long id);
    List<ToolConfig> getEnabledToolsOfAgent(Long agentId);
    List<SkillPackage> getEnabledSkillsOfAgent(Long agentId);
    List<Workflow> getEnabledWorkflowsOfAgent(Long agentId);
    List<McpServerToolsVO> getEnabledMcpOfAgent(Long agentId);

    /**
     * 智能体绑定且启用的子智能体（轻量列：id/agentCode/name/description，
     * 供对话页 @ 引用——agentCode 小写即 LLM 的子智能体工具调用名）
     */
    List<AgentDefinition> getEnabledSubAgentsOfAgent(Long agentId);

    /**
     * 获取智能体头像（base64 data URL，未设置返回 null）。
     * 头像不进 VO、不随既有查询接口返回，避免 base64 撑大列表/详情响应。
     */
    String getAvatar(Long id);

    /**
     * 更新智能体头像（avatar 为空时清除）
     */
    Boolean updateAvatar(Long id, String avatar);
}

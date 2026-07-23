package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.entity.Workflow;
import lombok.Data;

import java.util.List;

/**
 * 对话页面智能体聚合上下文VO
 *
 * @author huxuehao
 */
@Data
public class AgentChatContextVO {

    /** 智能体详情 */
    private AgentDefinitionVO detail;

    /** 头像（base64 data URL） */
    private String avatar;

    /** 允许上传的文件类型 */
    private List<String> allowFileType;

    /** 启用的工具 */
    private List<ToolConfig> enabledTools;

    /** 启用的技能包 */
    private List<SkillPackage> enabledSkills;

    /** 启用的 MCP 服务及工具（按 server 分组，供 @ 提及具体 MCP 工具） */
    private List<McpServerToolsVO> enabledMcp;

    /** 启用的子智能体（轻量列；agentCode 小写即 LLM 工具调用名，供 @ 引用） */
    private List<AgentDefinition> enabledSubAgents;

    /** 启用且已发布的工作流（轻量列；name 即 LLM 工具调用名，供 @ 引用） */
    private List<Workflow> enabledWorkflows;
}

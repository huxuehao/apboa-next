package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.entity.ToolConfig;
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
}

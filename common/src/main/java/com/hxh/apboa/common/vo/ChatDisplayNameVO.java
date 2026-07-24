package com.hxh.apboa.common.vo;

import lombok.Data;

import java.util.Map;

/**
 * 对话显示名映射VO
 *
 * @author huxuehao
 */
@Data
public class ChatDisplayNameVO {

    /** agentCode(小写) -> 智能体名称 */
    private Map<String, String> agents;

    /** toolId -> 工具名称 */
    private Map<String, String> tools;

    /** MCP toolName -> 「MCP服务名 · 工具名」 */
    private Map<String, String> mcpTools;
}

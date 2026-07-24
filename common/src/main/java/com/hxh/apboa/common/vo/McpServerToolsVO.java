package com.hxh.apboa.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 对话页 @ 提及用：agent 绑定的某个 MCP 服务及其可 @ 的工具（按 server 分组）。
 * 工具名 name 即 LLM 调用名，也是前端 &lt;agent-mcp&gt; 标签的 content。
 *
 * @author vaulka
 */
@Data
@AllArgsConstructor
public class McpServerToolsVO {

    /** MCP 服务 ID */
    private Long serverId;

    /** MCP 服务名 */
    private String serverName;

    /** 该服务下可 @ 的工具 */
    private List<McpToolBrief> tools;

    @Data
    @AllArgsConstructor
    public static class McpToolBrief {
        /** 工具名（= LLM 调用名 = <agent-mcp> 标签 content） */
        private String name;
        /** 工具描述 */
        private String description;
    }
}

package com.hxh.apboa.node.mcp;

import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP调用节点配置。
 *
 * @author huxuehao
 */
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * MCP服务ID。
     */
    private Long mcpServerId;
    /**
     * MCP工具ID。
     */
    private Long mcpToolId;
}

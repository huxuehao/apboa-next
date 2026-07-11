package com.hxh.apboa.node.mcp;

import com.hxh.apboa.common.vo.McpToolDebugResultVO;

import java.util.Map;

/**
 * MCP节点执行器桥接接口。
 * 由 biz-mcp 模块实现，避免 workflow 模块直接依赖 biz-mcp 造成循环依赖。
 *
 * @author huxuehao
 */
public interface McpNodeExecutor {
    /**
     * 调用MCP工具。
     *
     * @param toolId MCP工具ID
     * @param params 工具调用参数（key为参数名，value为参数值）
     * @return 调用结果
     */
    McpToolDebugResultVO execute(Long toolId, Map<String, Object> params);
}

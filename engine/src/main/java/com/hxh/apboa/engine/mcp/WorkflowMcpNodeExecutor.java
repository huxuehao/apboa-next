package com.hxh.apboa.engine.mcp;

import com.hxh.apboa.common.vo.McpToolDebugResultVO;
import com.hxh.apboa.mcp.service.McpToolDebugService;
import com.hxh.apboa.node.mcp.McpNodeExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工作流MCP节点执行器。
 * 复用 McpToolDebugService 的短生命周期客户端模式调用MCP工具。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowMcpNodeExecutor implements McpNodeExecutor {

    private final McpToolDebugService mcpToolDebugService;

    @Override
    public McpToolDebugResultVO execute(Long toolId, Map<String, Object> params) {
        return mcpToolDebugService.debugTool(toolId, params);
    }
}

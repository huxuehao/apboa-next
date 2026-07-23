package com.hxh.apboa.console.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.McpServer;
import com.hxh.apboa.common.entity.McpTool;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.vo.ChatDisplayNameVO;
import com.hxh.apboa.mcp.service.McpServerService;
import com.hxh.apboa.mcp.service.McpToolService;
import com.hxh.apboa.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：对话显示名 Controller
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/agent/chat/display-name")
@RequiredArgsConstructor
public class ChatDisplayNameController {

    private final AgentDefinitionService agentDefinitionService;
    private final ToolService toolService;
    private final McpServerService mcpServerService;
    private final McpToolService mcpToolService;

    /**
     * 查询对话显示名映射（子智能体/工具/MCP 工具三张名称表，匿名对话 chatKey 亦可访问）
     *
     * @return 显示名映射
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping
    public R<ChatDisplayNameVO> get() {
        ChatDisplayNameVO vo = new ChatDisplayNameVO();
        vo.setAgents(agentNameMap());
        vo.setTools(toolNameMap());
        vo.setMcpTools(mcpToolNameMap());
        return R.data(vo);
    }

    /** agentCode(小写) -> 智能体名称 */
    private Map<String, String> agentNameMap() {
        List<AgentDefinition> agents = agentDefinitionService.list(
                new LambdaQueryWrapper<AgentDefinition>()
                        .select(AgentDefinition::getAgentCode, AgentDefinition::getName));

        Map<String, String> map = new HashMap<>();
        for (AgentDefinition a : agents) {
            if (a.getAgentCode() != null && a.getName() != null) {
                map.putIfAbsent(a.getAgentCode().toLowerCase(), a.getName());
            }
        }
        return map;
    }

    /** toolId -> 工具名称 */
    private Map<String, String> toolNameMap() {
        List<ToolConfig> tools = toolService.list(
                new LambdaQueryWrapper<ToolConfig>()
                        .select(ToolConfig::getToolId, ToolConfig::getName));

        Map<String, String> map = new HashMap<>();
        for (ToolConfig t : tools) {
            if (t.getToolId() != null && t.getName() != null) {
                map.putIfAbsent(t.getToolId(), t.getName());
            }
        }
        return map;
    }

    /** MCP toolName -> 「MCP服务名 · 工具名」（后端一次性按 serverIds 批量聚合，替代前端逐服务 N+1 请求） */
    private Map<String, String> mcpToolNameMap() {
        List<McpServer> servers = mcpServerService.list(
                new LambdaQueryWrapper<McpServer>()
                        .select(McpServer::getId, McpServer::getName));

        Map<Long, String> serverNameById = new HashMap<>();
        for (McpServer s : servers) {
            serverNameById.put(s.getId(), s.getName());
        }

        List<McpTool> tools = mcpToolService.listByServerIds(new ArrayList<>(serverNameById.keySet()));

        Map<String, String> map = new HashMap<>();
        for (McpTool t : tools) {
            String serverName = serverNameById.get(t.getMcpServerId());
            if (serverName != null && t.getToolName() != null) {
                map.putIfAbsent(t.getToolName(), serverName + " · " + t.getToolName());
            }
        }
        return map;
    }
}

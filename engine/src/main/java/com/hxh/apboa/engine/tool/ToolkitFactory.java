package com.hxh.apboa.engine.tool;

import com.hxh.apboa.agent.service.AgentCodeExecutionService;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.agent.service.AgentSubAgentService;
import com.hxh.apboa.agent.service.CodeExecutionConfigService;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.engine.agent.A2aAgentHelper;
import com.hxh.apboa.engine.agent.ReActAgentHelper;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.mcp.McpClientFactory;
import com.hxh.apboa.engine.tool.dynamices.DynamicAgentTool;
import com.hxh.apboa.engine.workspace.tool.SearchReplaceFileTool;
import com.hxh.apboa.tool.service.AgentToolService;
import com.hxh.apboa.tool.service.ToolService;
import com.hxh.apboa.workflowbiz.core.WorkflowDefinitionCompiler;
import com.hxh.apboa.workflowbiz.service.AgentWorkflowService;
import com.hxh.apboa.workflowbiz.service.WorkflowRunService;
import com.hxh.apboa.workflowbiz.service.WorkflowService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 描述：工具工厂
 *
 * @author huxuehao
 */
@Slf4j
@Component
public class ToolkitFactory {
    private final ToolService toolService;
    private final AgentToolService agentToolService;
    private final AgentSubAgentService agentSubAgentService;
    private final ReActAgentHelper reActAgentHelper;
    private final A2aAgentHelper a2aAgentHelper;
    private final McpClientFactory mcpClientFactory;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentCodeExecutionService agentCodeExecutionService;
    private final CodeExecutionConfigService codeExecutionConfigService;
    private final CustomToolkitConfig customToolkitConfig;
    private final AgentWorkflowService agentWorkflowService;
    private final WorkflowService workflowService;
    private final WorkflowRunService workflowRunService;
    private final WorkflowDefinitionCompiler workflowDefinitionCompiler;

    public ToolkitFactory(ToolService toolService,
                          AgentToolService agentToolService,
                          AgentSubAgentService agentSubAgentService,
                          @Lazy
                          ReActAgentHelper reActAgentHelper,
                          @Lazy
                          A2aAgentHelper a2aAgentHelper,
                          McpClientFactory mcpClientFactory,
                          AgentCodeExecutionService agentCodeExecutionService,
                          CodeExecutionConfigService codeExecutionConfigService,
                          AgentDefinitionService agentDefinitionService,
                          AgentWorkflowService agentWorkflowService,
                          WorkflowService workflowService,
                          WorkflowRunService workflowRunService,
                          WorkflowDefinitionCompiler workflowDefinitionCompiler,
                          CustomToolkitConfig customToolkitConfig) {
        this.toolService = toolService;
        this.agentToolService = agentToolService;
        this.agentSubAgentService = agentSubAgentService;
        this.reActAgentHelper = reActAgentHelper;
        this.a2aAgentHelper = a2aAgentHelper;
        this.mcpClientFactory = mcpClientFactory;
        this.agentCodeExecutionService = agentCodeExecutionService;
        this.codeExecutionConfigService = codeExecutionConfigService;
        this.agentDefinitionService = agentDefinitionService;
        this.agentWorkflowService = agentWorkflowService;
        this.workflowService = workflowService;
        this.workflowRunService = workflowRunService;
        this.workflowDefinitionCompiler = workflowDefinitionCompiler;
        this.customToolkitConfig = customToolkitConfig;
    }


    public Toolkit getToolkit(AgentDefinition agentDefinition) {
        List<Long> toolIds = agentToolService.getToolIds(agentDefinition.getId());
        Toolkit toolkit = getToolkit(toolIds);

        if (!toolIds.isEmpty()) {
            // 注册工具
            registerTools(toolkit, toolService.listByIds(toolIds));
        }

        // 注册工作流
        List<Long> workflowIds = agentWorkflowService.getWorkflowIds(agentDefinition.getId());
        if (!workflowIds.isEmpty()) {
            registerWorkflows(toolkit, workflowIds);
        }


        // 注册文件搜索替换工具
        Long codeExecutionId = agentCodeExecutionService.getCodeExecutionIdByAgentId(agentDefinition.getId());
        if (codeExecutionId != null) {
            CodeExecutionConfig config = codeExecutionConfigService.getById(codeExecutionId);
            if (config != null && config.getEnabled() && config.getEnableWrite()) {
                toolkit.registerTool(new SearchReplaceFileTool());
            }
        }

        // 此处仅注册缓存的 MCP 工具模式，真正的 MCP 连接会在调用时打开。
        mcpClientFactory.getLazyMcpTools(agentDefinition).forEach(toolkit::registerAgentTool);

        // 注册 Agent as Tool
        List<Long> subAgentIds = agentSubAgentService.getSubAgentIds(agentDefinition.getId());
        if (!subAgentIds.isEmpty()) {
            registerSubAgents(toolkit, subAgentIds);
        }

        // 注册文档附件文本读取工具（所有智能体无条件注册）
        toolkit.registerTool(new LoadFileTextContentTool());

        return toolkit;
    }

    public Toolkit getToolkit(List<Long> toolIds) {
        Toolkit toolkit = new Toolkit(
                ToolkitConfig.builder()
                        // 是否允许并行执行多个工具
                        .parallel(customToolkitConfig.isParallel())
                        // 是否允许删除工具
                        .allowToolDeletion(customToolkitConfig.isAllowToolDeletion())
                        // 设置工具执行超时时间
                        .executionConfig(customToolkitConfig.toExecutionConfig())
                        .build());
        if (!toolIds.isEmpty()) {
            // 注册工具
            toolService.listByIds(toolIds)
                    .stream()
                    .filter(ToolConfig::getEnabled)
                    .forEach(toolConfig -> {
                        // 内置工具注册
                        if (toolConfig.getToolType() == ToolType.BUILTIN) {
                            toolkit.registerTool(ToolsRegister.getTool(toolConfig.getClassPath()));
                        } else {
                            // 动态工具注册
                            toolkit.registerTool(new DynamicAgentTool(toolConfig));
                        }

                        // §6.4：确认是否生效只取决于工具自身 need_confirm，与 memoryActive 解耦
                        // （修 §2.5 Bug3：不开记忆时 need_confirm 工具被移出清单导致裸跑的安全漏洞）
                        if (Boolean.TRUE.equals(toolConfig.getNeedConfirm())) {
                            IConfirmationHook.setNeedConfirmTool(toolConfig.getToolId());
                        } else {
                            IConfirmationHook.removeNeedConfirmTool(toolConfig.getToolId());
                        }
                    });
        }
        return toolkit;
    }

    /**
     * 注册工具
     *
     * @param toolkit     工具集
     * @param toolConfigs 工具配置列表
     */
    public static void registerTools(Toolkit toolkit, List<ToolConfig> toolConfigs) {
        if (toolConfigs == null || toolConfigs.isEmpty()) {
            return;
        }

        toolConfigs.stream()
                .filter(ToolConfig::getEnabled)
                .forEach(toolConfig -> {
                    AgentTool tool = toolkit.getTool(toolConfig.getToolId());
                    // 工具不存在时
                    if (tool == null) {
                        // 内置工具注册
                        if (toolConfig.getToolType() == ToolType.BUILTIN) {
                            toolkit.registerTool(ToolsRegister.getTool(toolConfig.getClassPath()));
                        } else {
                            // 动态工具注册
                            toolkit.registerTool(new DynamicAgentTool(toolConfig));
                        }

                        if (toolConfig.getNeedConfirm()) {
                            IConfirmationHook.setNeedConfirmTool(toolConfig.getToolId());
                        } else {
                            IConfirmationHook.removeNeedConfirmTool(toolConfig.getToolId());
                        }
                    }
                });
    }

    /**
     * 注册工作流
     *
     * @param toolkit     工具集
     * @param workflowIds 工作流ID
     */
    public void registerWorkflows(Toolkit toolkit, List<Long> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return;
        }

        List<Workflow> workflows = workflowService.listByIds(workflowIds);
        workflows.stream()
                .filter(Workflow::getEnabled)
                .forEach(workflow -> {
                    WorkflowVersion workflowVersion = workflowService.latestPublishedVersion(workflow.getId());
                    if (workflowVersion != null) {
                        toolkit.registerTool(new WorkflowTool(
                                workflow,
                                workflowVersion,
                                workflowRunService,
                                workflowDefinitionCompiler)
                        );
                    }
                });
    }

    private void registerSubAgents(Toolkit toolkit, List<Long> subAgentIds) {
        for (Long subAgentId : subAgentIds) {
            AgentDefinition definition = agentDefinitionService.getById(subAgentId);

            if (definition == null || !definition.getEnabled()) {
                continue;
            }

            try {
                // Agent as Tool
                switch (definition.getAgentType()) {
                    case CUSTOM:
                        toolkit.registration()
                                .subAgent(() -> createTrackedSubAgent(definition),
                                        createSubAgentConfig(definition))
                                .apply();
                        break;
                    case A2A:
                        toolkit.registration()
                                .subAgent(() -> a2aAgentHelper.getA2aAgent(definition),
                                        createSubAgentConfig(definition))
                                .apply();
                        break;
                    default:
                        break;
                }
                log.debug("Register sub agent: {}", subAgentId);
            } catch (Exception e) {
                log.error("Registration of sub agent failed: {}", subAgentId, e);
            }
        }
    }

    private SubAgentConfig createSubAgentConfig(AgentDefinition definition) {
        return SubAgentConfig.builder()
                .toolName(definition.getAgentCode().toLowerCase())
                .description(definition.getDescription() != null ?
                        definition.getDescription() : definition.getName())
                .forwardEvents(true)
                .build();
    }

    /**
     * 创建子智能体并登记归属元数据。
     * provide() 由 SubAgentTool 在主 run 的执行线程内同步调用，此时 AgentContext 已初始化
     * （SubAgentTool 先 AgentContext.init 再 provide），可取到主会话 threadId；
     * ChatLogHook 凭 subParentThreadId 识别子智能体事件、收集中间过程并挂到主会话的 tool 消息上
     */
    private ReActAgent createTrackedSubAgent(AgentDefinition definition) {
        ReActAgent sub = reActAgentHelper.getReActAgent(definition, true);
        AgentContext.getIfExists()
                .map(AgentContext::getThreadId)
                .ifPresent(parentThreadId -> {
                    AgentMetadataStore.put(sub.getAgentId(), "subParentThreadId", parentThreadId);
                    AgentMetadataStore.put(sub.getAgentId(), "subToolName", definition.getAgentCode().toLowerCase());
                });
        return sub;
    }
}

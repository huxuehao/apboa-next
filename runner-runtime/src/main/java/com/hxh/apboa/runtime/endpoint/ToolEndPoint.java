package com.hxh.apboa.runtime.endpoint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.dto.ToolDebugDTO;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.vo.ToolDebugResultVO;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.tool.IAgentTool;
import com.hxh.apboa.engine.tool.ToolsRegister;
import com.hxh.apboa.engine.tool.dynamices.IDynamicAgentTool;
import com.hxh.apboa.engine.tool.dynamices.ToolInstanceLoadFactory;
import com.hxh.apboa.engine.tool.dynamices.ToolInstanceLoader;
import com.hxh.apboa.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 描述：执行工具调用
 *
 * @author huxuehao
 **/
@Slf4j
@RestController
@RequestMapping("/runtime/agent")
@RequiredArgsConstructor
public class ToolEndPoint {
    private final ToolService toolService;
    private final ToolReflectionInvoker toolReflectionInvoker;

    @SkAccess
    @ChatKeyAccess
    @PostMapping("/do/{toolName}/tool")
    public R<?> doTool(@PathVariable("toolName") String toolName , @RequestBody LinkedHashMap<String, Object> args) {
        ToolConfig toolConfig = toolService.getOne(new LambdaQueryWrapper<ToolConfig>().eq(ToolConfig::getToolId, toolName));

        if (toolConfig == null) {
            return R.data("工具调用失败");
        }

        return R.data(executeTool(toolConfig, args));
    }

    /**
     * 调试执行工具（管理台专用，仅登录态 + EDITOR 以上角色，不对 SK/ChatKey 开放）。
     * 调试即真实执行，不走 needConfirm（HITL）确认，结果包装与 MCP 工具调试对齐。
     */
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    @PostMapping("/tool/debug")
    public R<ToolDebugResultVO> debugTool(@RequestBody ToolDebugDTO dto) {
        ToolConfig toolConfig = toolService.getById(dto.getId());
        if (toolConfig == null) {
            throw new BusinessException("工具不存在");
        }
        if (!Boolean.TRUE.equals(toolConfig.getEnabled())) {
            throw new BusinessException("工具已禁用，无法调试");
        }

        ToolDebugResultVO result = new ToolDebugResultVO();
        result.setToolName(toolConfig.getName());
        result.setExecutedAt(LocalDateTime.now());

        Map<String, Object> input = dto.getInput() != null ? dto.getInput() : Map.of();
        long start = System.currentTimeMillis();
        try {
            result.setSuccess(true);
            result.setContent(executeTool(toolConfig, input));
        } catch (Exception e) {
            log.warn("Tool debug call failed for '{}': {}", toolConfig.getToolId(), e.getMessage());
            result.setSuccess(false);
            // 反射调用失败时原始原因包在 cause 里，优先取有信息量的消息
            Throwable root = e.getCause() != null ? e.getCause() : e;
            result.setErrorMessage(root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName());
        }
        result.setDurationMs(System.currentTimeMillis() - start);
        return R.data(result);
    }

    /**
     * 按工具类型执行调用：内置工具走注册表反射，自定义工具走动态加载器
     */
    private Object executeTool(ToolConfig toolConfig, Map<String, Object> args) {
        if (toolConfig.getToolType() == ToolType.BUILTIN) {
            IAgentTool iTool = ToolsRegister.getTool(toolConfig.getClassPath());
            if (iTool == null) {
                throw new BusinessException("内置工具未注册: " + toolConfig.getClassPath());
            }
            return toolReflectionInvoker.invokeTool(iTool, toolConfig.getToolId(), args);
        }

        // 拿到动态工具的实例
        ToolInstanceLoader loader = ToolInstanceLoadFactory.getInstanceLoader(toolConfig.getLanguage());
        if (loader == null) {
            throw new BusinessException("暂不支持该语言的动态工具: " + toolConfig.getLanguage());
        }
        IDynamicAgentTool dynamicAgentTool = loader.loadInstance(toolConfig.getCode());

        AgentContext agentContext = new AgentContext();
        agentContext.setParams(Map.of());
        return dynamicAgentTool.execute(agentContext, args);
    }
}

package com.hxh.apboa.runtime.endpoint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.tool.IAgentTool;
import com.hxh.apboa.engine.tool.ToolsRegister;
import com.hxh.apboa.engine.tool.dynamices.IDynamicAgentTool;
import com.hxh.apboa.engine.tool.dynamices.ToolInstanceLoadFactory;
import com.hxh.apboa.tool.service.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 描述：执行工具调用
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/runtime/tool")
@RequiredArgsConstructor
public class ToolEndPoint {
    private final ToolService toolService;
    private final ToolReflectionInvoker toolReflectionInvoker;

    @SkAccess
    @ChatKeyAccess
    @PostMapping("/{toolName}/do")
    public R<?> doTool(@PathVariable("toolName") String toolName , @RequestBody LinkedHashMap<String, Object> args) {
        ToolConfig toolConfig = toolService.getOne(new LambdaQueryWrapper<ToolConfig>().eq(ToolConfig::getToolId, toolName));

        if (toolConfig == null) {
            return R.data("工具调用失败");
        }

        if (toolConfig.getToolType() == ToolType.BUILTIN) {
            IAgentTool iTool = ToolsRegister.getTool(toolConfig.getClassPath());
            Object result = toolReflectionInvoker.invokeTool(iTool, toolName, args);

            return R.data(result);
        } else {
            // 拿到动态工具的实例
            IDynamicAgentTool dynamicAgentTool = ToolInstanceLoadFactory
                    .getInstanceLoader(toolConfig.getLanguage()).loadInstance(toolConfig.getCode());

            AgentContext agentContext = new AgentContext();
            agentContext.setParams(Map.of());
            Object  result = dynamicAgentTool.execute(agentContext, args);

            return R.data(result);
        }
    }
}

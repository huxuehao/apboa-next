package com.hxh.apboa.engine.tool;

import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.engine.tool.dynamices.IDynamicAgentTool;
import com.hxh.apboa.engine.tool.dynamices.ToolInstanceLoadFactory;
import com.hxh.apboa.node.toolexecute.ToolNodeExecutor;
import com.hxh.apboa.tool.service.ToolService;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 工作流工具节点执行器。
 * 在 workflow 节点中执行平台已注册的内置或自定义工具。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowToolNodeExecutor implements ToolNodeExecutor {

    private final ToolService toolService;

    @Override
    public Object execute(Long toolId, Map<String, Object> params) {
        ToolConfig toolConfig = toolService.getById(toolId);
        if (toolConfig == null) {
            throw new RuntimeException("工具不存在，ID: " + toolId);
        }
        if (!Boolean.TRUE.equals(toolConfig.getEnabled())) {
            throw new RuntimeException("工具未启用，ID: " + toolId);
        }

        if (toolConfig.getToolType() == ToolType.BUILTIN) {
            return executeBuiltinTool(toolConfig, params);
        } else {
            return executeCustomTool(toolConfig, params);
        }
    }

    /**
     * 执行内置工具。
     * 从 ToolsRegister 获取 IAgentTool 实例，反射调用 @Tool 标注的方法。
     */
    private Object executeBuiltinTool(ToolConfig toolConfig, Map<String, Object> params) {
        IAgentTool tool = ToolsRegister.getTool(toolConfig.getClassPath());
        if (tool == null) {
            throw new RuntimeException("内置工具未注册，classPath: " + toolConfig.getClassPath());
        }

        Method toolMethod = findToolMethod(tool.getClass());
        if (toolMethod == null) {
            throw new RuntimeException("内置工具未找到 @Tool 注解方法，classPath: " + toolConfig.getClassPath());
        }

        Object[] args = resolveArgs(toolMethod, params, toolConfig.getInputSchema());
        try {
            toolMethod.setAccessible(true);
            return toolMethod.invoke(tool, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("工具方法不可访问: " + toolMethod.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("工具执行异常: " + cause.getMessage(), cause);
        }
    }

    /**
     * 执行自定义工具。
     * 通过 GroovyToolInstanceLoader 加载用户编写的代码并执行。
     */
    private Object executeCustomTool(ToolConfig toolConfig, Map<String, Object> params) {
        if (FuncUtils.isEmpty(toolConfig.getCode())) {
            throw new RuntimeException("自定义工具代码为空，工具ID: " + toolConfig.getId());
        }

        IDynamicAgentTool dynamicTool = ToolInstanceLoadFactory
                .getInstanceLoader(toolConfig.getLanguage())
                .loadInstance(toolConfig.getCode());

        return dynamicTool.execute(null, params);
    }

    /**
     * 查找类中第一个被 @Tool 注解标记的方法。
     */
    private Method findToolMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return method;
            }
        }
        // 也检查父类方法
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return findToolMethod(superClass);
        }
        return null;
    }

    /**
     * 解析方法参数值。
     * 根据 inputSchema 的参数名和类型，从 params Map 中取值并转换类型。
     */
    private Object[] resolveArgs(Method method, Map<String, Object> params, JsonNode inputSchema) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String paramName = getParamName(param);
            Object rawValue = params.get(paramName);

            if (rawValue == null) {
                // 检查 inputSchema 中的默认值
                String defaultValue = getDefaultValue(inputSchema, paramName);
                if (defaultValue != null) {
                    rawValue = defaultValue;
                }
            }

            args[i] = convertArg(rawValue, param.getType());
        }

        return args;
    }

    /**
     * 从 @ToolParam 注解获取参数名。
     */
    private String getParamName(Parameter param) {
        if (param.isAnnotationPresent(ToolParam.class)) {
            ToolParam annotation = param.getAnnotation(ToolParam.class);
            if (!FuncUtils.isEmpty(annotation.name())) {
                return annotation.name();
            }
        }
        return param.getName();
    }

    /**
     * 从 inputSchema 中获取参数的默认值。
     */
    private String getDefaultValue(JsonNode inputSchema, String paramName) {
        if (inputSchema == null || !inputSchema.isArray()) {
            return null;
        }
        for (JsonNode node : inputSchema) {
            if (node.has("name") && paramName.equals(node.get("name").asText())) {
                if (node.has("defaultValue") && !node.get("defaultValue").isNull()) {
                    return node.get("defaultValue").asText();
                }
            }
        }
        return null;
    }

    /**
     * 参数类型转换。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertArg(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        String str = value.toString();
        if (targetType == String.class) {
            return str;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.valueOf(str);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.valueOf(str);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.valueOf(str);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.valueOf(str);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.valueOf(str);
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, str);
        }
        return value;
    }
}

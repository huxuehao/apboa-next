package com.hxh.apboa.engine.hitl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.entity.McpTool;
import com.hxh.apboa.common.entity.ToolConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HITL 确认字段元数据归一器：把两种参数 schema 格式统一成确认 UI 可渲染的 fields 列表，
 * 供工具注册时登记进 {@code IConfirmationHook}，随 TOOL_CONFIRM_REQUIRED / pending 下发。
 *
 * <p>两种来源格式（实测确认）：
 * <ul>
 *   <li>普通工具 {@code tool_config.input_schema}：自定义 JSON 数组
 *       {@code [{name, description, type, defaultValue, required}]}；</li>
 *   <li>MCP 工具 {@code mcp_tool.input_schema}：标准 JSON Schema
 *       {@code {type:"object", properties:{...}, required:[...]}}。</li>
 * </ul>
 *
 * <p>归一后的元素形状（Map 直接进事件载荷序列化）：
 * {@code {name, type, required, description, options?}}——options 仅在 MCP schema
 * 声明 enum 时存在。不含展示名翻译（确认表单兜底层定位为开发人员核对，
 * 展示回退链 description → name 由前端处理）；定制确认组件自带业务语义，不依赖本元数据。
 *
 * @author vaulka
 */
public final class ConfirmFieldMetaBuilder {

    private ConfirmFieldMetaBuilder() {}

    /** 普通/内置工具：解析 tool_config.input_schema 自定义数组。结构异常返回空列表（降级 JSON 展示）。 */
    public static List<Map<String, Object>> fromToolConfig(ToolConfig toolConfig) {
        JsonNode schema = toolConfig == null ? null : toolConfig.getInputSchema();
        if (schema == null || !schema.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (JsonNode param : schema) {
            String name = param.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", name);
            field.put("type", param.path("type").asText("string"));
            field.put("required", param.path("required").asBoolean(false));
            String description = param.path("description").asText(null);
            if (description != null && !description.isBlank()) {
                field.put("description", description);
            }
            fields.add(field);
        }
        return fields;
    }

    /** MCP 工具：解析 mcp_tool.input_schema 标准 JSON Schema。结构异常返回空列表（降级 JSON 展示）。 */
    public static List<Map<String, Object>> fromMcpTool(McpTool mcpTool) {
        JsonNode schema = mcpTool == null ? null : mcpTool.getInputSchema();
        JsonNode properties = schema == null ? null : schema.path("properties");
        if (properties == null || !properties.isObject()) {
            return List.of();
        }
        List<String> requiredNames = new ArrayList<>();
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            required.forEach(n -> requiredNames.add(n.asText()));
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        properties.fields().forEachRemaining(entry -> {
            JsonNode prop = entry.getValue();
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", entry.getKey());
            field.put("type", prop.path("type").asText("string"));
            field.put("required", requiredNames.contains(entry.getKey()));
            String description = prop.path("description").asText(null);
            if (description != null && !description.isBlank()) {
                field.put("description", description);
            }
            JsonNode enums = prop.path("enum");
            if (enums.isArray() && !enums.isEmpty()) {
                List<Object> options = new ArrayList<>();
                enums.forEach(n -> options.add(n.isValueNode() ? n.asText() : n.toString()));
                field.put("options", options);
            }
            fields.add(field);
        });
        return fields;
    }
}

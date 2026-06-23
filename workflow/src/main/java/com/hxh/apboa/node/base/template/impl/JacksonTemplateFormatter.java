package com.hxh.apboa.node.base.template.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.node.base.template.EngineCapability;
import com.hxh.apboa.node.base.template.FormatterType;
import com.hxh.apboa.node.base.template.TemplateFormatter;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 描述：基于 Jackson 的模板格式化器
 * <p>
 * 分为纯文本格式化和json对象
 * 【纯文本格式化】：
 * - 使用 ${变量名} 定义占位，且 ${变量名} 必须被英文引号包裹
 * - 支持多变量占位：例如：
 *   - "欢迎你，${userName}"
 *   - "欢迎你，${userName}，${userAge}"
 * - 支持仅变量占位：例如："${userName}"
 * <p>
 * 【json对象格式化】：
 * - 使用 ${变量名} 定义占位，且 ${变量名} 必须被英文引号包裹
 * - 不支持对 key 值的占位，例如：
 *   {
 *       "${age}": 18
 *   }
 * - 不支持 value 值字符串组合占位，例如：
 *   {
 *       "name": "胡 ${userName}"
 *   }
 * - 支持的场景如下：
 *   {
 *       "response": {
 *           "header": {
 *               "code": 200,
 *               "message": "${message}"
 *           },
 *           "body": {
 *               "data": "${data}",
 *               "pagination": {
 *                   "page": "${page}",
 *                   "size": "${size}"
 *                   "total": "${total}"
 *               }
 *           },
 *           "other": ["${test}"]
 *       }
 *   }
 *
 * <p>
 * 使用场景：
 * - 动态生成JSON响应报文，保留变量数据类型
 * - 基于模板的动态文本生成
 *
 * @author huxuehao
 **/
public class JacksonTemplateFormatter implements TemplateFormatter {
    private final ObjectMapper objectMapper;
    private final Pattern variablePattern = Pattern.compile("\\$\\{([^}]+)}");

    public JacksonTemplateFormatter() {
        this.objectMapper = new ObjectMapper();
        // 忽略空对象
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public Object format(String template, Map<String, Object> variables) {
        return format(template, variables, true);
    }

    @Override
    public Object format(String template, Map<String, Object> variables, boolean tryToObj) {
        try {
            // 将模板字符串转换为JSON树
            JsonNode templateNode = objectMapper.readTree(template);
            // 处理模板树
            JsonNode resultNode = processNode(templateNode, variables);

            // 返回结果
            if (tryToObj) {
                return objectMapper.treeToValue(resultNode, Object.class);
            } else {
                return resultNode.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("Jackson模板格式化失败", e);
        }
    }

    /**
     * 递归处理 JSON 节点，替换其中的变量
     */
    private JsonNode processNode(JsonNode node, Map<String, Object> variables) {
        if (node.isTextual()) {
            // 文本节点：检查并替换变量
            String text = node.asText();
            return resolveTextNode(text, variables);
        } else if (node.isObject()) {
            // 对象节点：递归处理每个字段
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                result.set(entry.getKey(), processNode(entry.getValue(), variables));
            });
            return result;
        } else if (node.isArray()) {
            // 数组节点：递归处理每个元素
            ArrayNode result = objectMapper.createArrayNode();
            for (JsonNode element : node) {
                result.add(processNode(element, variables));
            }
            return result;
        } else {
            // 其他类型节点（数字、布尔值、null）：直接返回
            return node;
        }
    }

    /**
     * 处理文本节点中的变量替换
     *
     * @param text 文本
     * @param variables 可使用的变量
     * @return 处理完成的JSON树
     */
    private JsonNode resolveTextNode(String text, Map<String, Object> variables) {
        // 检查是否包含变量
        if (!containsVariable(text)) {
            return objectMapper.valueToTree(text);
        }

        // 如果是纯变量（整个文本就是一个变量），直接替换
        if (isPureVariable(text)) {
            String varName = extractVariableName(text);
            Object value = getVariableValue(varName, variables);
            return objectMapper.valueToTree(value);
        }

        // 如果是包含变量的字符串，进行模板替换
        String result = replaceVariablesInText(text, variables);

        // 尝试解析替换后的结果
        return parseResultText(result);
    }

    /**
     * 检查文本是否包含变量
     */
    private boolean containsVariable(String text) {
        return text.contains("${");
    }

    /**
     * 检查是否是纯变量（整个文本就是一个变量引用）
     */
    private boolean isPureVariable(String text) {
        return text.matches("\\$\\{([^}]+)}");
    }

    /**
     * 提取变量名
     */
    private String extractVariableName(String text) {
        return text.substring(2, text.length() - 1).trim();
    }

    /**
     * 获取变量值
     */
    private Object getVariableValue(String varName, Map<String, Object> variables) {
        Object value = variables.get(varName);
        if (value == null) {
            return "${"+varName+"}";
        }
        return value;
    }

    /**
     * 在文本中替换变量
     */
    private String replaceVariablesInText(String text, Map<String, Object> variables) {
        return replaceVariablesInText(text, variables, variablePattern);
    }

    /**
     * 解析替换后的文本
     */
    private JsonNode parseResultText(String text) {
        try {
            // 尝试解析为JSON
            return objectMapper.readTree(text);
        } catch (Exception e) {
            try {
                // 尝试解析为JSON数组
                if (text.trim().startsWith("[")) {
                    return objectMapper.readTree(text);
                }
            } catch (Exception e2) {
                // 忽略，继续尝试其他方式
            }

            // 如果无法解析为JSON，作为字符串返回
            return objectMapper.valueToTree(text);
        }
    }


    @Override
    public FormatterType getEngineType() {
        return FormatterType.JACKSON;
    }

    @Override
    public EngineCapability getCapability() {
        return new EngineCapability(false, false, false, false);
    }
}

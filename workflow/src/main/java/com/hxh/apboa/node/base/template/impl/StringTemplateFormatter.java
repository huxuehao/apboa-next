package com.hxh.apboa.node.base.template.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.node.base.template.EngineCapability;
import com.hxh.apboa.node.base.template.FormatterType;
import com.hxh.apboa.node.base.template.TemplateFormatter;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 描述：简单字符串模板格式化器
 * StringTemplateFormatter的作用是使用变量的toString()的结果直接对变量进行替换。
 * 使用示例："今天星期${day}，天气${weather}，适合跑步"
 * <p>
 * 使用场景：
 * - 简单的字符串占位变量
 *
 * @author huxuehao
 **/
public class StringTemplateFormatter implements TemplateFormatter {
    private final Pattern variablePattern = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public Object format(String template, Map<String, Object> variables) {
        return format(template, variables, true);
    }

    @Override
    public Object format(String template, Map<String, Object> variables, boolean tryToObj) {
        try {
            String result = replaceVariables(template, variables);

            if (tryToObj) {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(result, Object.class);
            } else {
                return result;
            }

        } catch (Exception e) {
            // 如果不是JSON，返回字符串
            return replaceVariables(template, variables);
        }
    }

    private String replaceVariables(String template, Map<String, Object> variables) {
        return replaceVariablesInText(template, variables, variablePattern);
    }

    @Override
    public FormatterType getEngineType() {
        return FormatterType.STRING;
    }

    @Override
    public EngineCapability getCapability() {
        return new EngineCapability(false, false, false, false);
    }
}

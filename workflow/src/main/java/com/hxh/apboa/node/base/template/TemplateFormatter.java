package com.hxh.apboa.node.base.template;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 描述：模板格式化器接口 - 定义统一的模板格式化契约
 *
 * @author huxuehao
 **/
public interface TemplateFormatter {
    /**
     * 使用模板格式化输出
     *
     * @param template 模板字符串，支持变量替换和表达式
     * @param variables 变量上下文
     * @return 格式化后的对象
     */
    Object format(String template, Map<String, Object> variables);

    /**
     * 使用模板格式化输出
     *
     * @param template 模板字符串，支持变量替换和表达式
     * @param variables 变量上下文
     * @param tryToObj 是否尝试将结果转换为对象
     * @return 格式化后的对象
     */
    Object format(String template, Map<String, Object> variables, boolean tryToObj);

    /**
     * 验证模板语法是否正确
     *
     * @param template 模板字符串
     * @return 验证结果
     */
    default Boolean validate(String template) {
        try {
            // 尝试解析模板
            new ObjectMapper().readTree(template);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 预编译模板（可选操作）
     *
     * @param template 模板字符串
     * @return 编译后的模板标识，用于后续快速渲染
     */
    default String precompile(String template) {
        return template; // 默认实现返回原模板
    }

    /**
     * 获取支持的模板引擎类型
     */
    FormatterType getEngineType();

    /**
     * 获取引擎能力描述
     */
    default EngineCapability getCapability() {
        return new EngineCapability();
    }

    /**
     * 替换文本中的变量
     * @param text 文本
     * @param variables 变量集合
     * @param variablePattern 变量正则
     * @return 替换后的文本
     */
    default String replaceVariablesInText(String text, Map<String, Object> variables, Pattern variablePattern) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = variablePattern.matcher(text);
        // 匹配变量
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            // 替换变量
            String replacement = value != null ? value.toString() : "${"+varName+"}";
            // 将从上次匹配结束位置到当前匹配位置之间的文本，以及替换后的内容追加到 result 中
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        // 将最后一个匹配位置之后的剩余文本追加到
        matcher.appendTail(result);

        return result.toString();
    }
}

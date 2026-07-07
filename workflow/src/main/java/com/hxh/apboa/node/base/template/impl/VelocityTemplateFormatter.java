package com.hxh.apboa.node.base.template.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.node.base.template.EngineCapability;
import com.hxh.apboa.node.base.template.FormatterType;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.util.Map;

/**
 * 描述：Velocity 模板格式化器
 *
 * <p>
 * 该类基于 Velocity 模板引擎，实现了模板与数据的融合渲染，支持将结构化数据（如Map、对象）注入模板，
 * 最终生成动态内容（如JSON、HTML、文本等）并自动反序列化为Java对象。
 * <p>
 * 核心功能：
 * 1. 解析Velocity模板语法（变量引用、循环、条件判断等）
 * 2. 将输入的变量数据注入模板，生成渲染后的字符串
 * 3. 自动将渲染结果反序列化为Java对象（支持JSON格式字符串转换为Map/对象）
 * <p>
 * 使用场景：
 * - 动态生成JSON响应报文
 * - 生成动态配置文件、邮件内容、代码模板等
 * - 基于模板的动态文本生成
 *
 * @author huxuehao
 **/
public class VelocityTemplateFormatter implements TemplateFormatter {
    private final VelocityEngine velocityEngine;

    public VelocityTemplateFormatter() {
        this.velocityEngine = new VelocityEngine();
        velocityEngine.init();
    }

    /**
     * 核心方法：使用Velocity模板和变量数据生成动态内容，并转换为Java对象
     *
     * @param template  Velocity模板字符串，支持VTL语法（如${变量}、#if、#foreach等）
     * @param variables 模板中使用的变量集合（键为模板中的变量名，值为变量值）
     * @return 渲染后的内容反序列化得到的Java对象（如JSON字符串会转为Map或实体类）
     * @throws RuntimeException 当模板语法错误、渲染失败或反序列化失败时抛出
     * <p>
     * 使用示例：
     * <pre>
     * // 1. 定义模板（JSON格式示例）
     * String template = "{" +
     *     "\"name\": \"${user.name}\"," +
     *     "\"age\": ${user.age}," +
     *     "\"hobbies\": [#foreach($h in ${user.hobbies})\"$h\"#{if($foreach.hasNext)},#{end}#end]" +
     * "}";
     *
     * // 2. 准备变量数据
     * Map<String, Object> user = new HashMap<>();
     * user.put("name", "张三");
     * user.put("age", 25);
     * user.put("hobbies", Arrays.asList("读书", "运动"));
     * Map<String, Object> variables = Collections.singletonMap("user", user);
     *
     * // 3. 渲染模板
     * VelocityTemplateFormatter formatter = new VelocityTemplateFormatter();
     * Object result = formatter.format(template, variables);
     *
     * // 4. 结果为Map对象，可直接使用
     * // 输出: {"name":"张三","age":25,"hobbies":["读书","运动"]}
     * </pre>
     */
    @Override
    public Object format(String template, Map<String, Object> variables) {
        return format(template, variables, true);
    }

    @Override
    public Object format(String template, Map<String, Object> variables, boolean tryToObj) {
        try {
            VelocityContext context = new VelocityContext(variables);
            StringWriter writer = new StringWriter();
            velocityEngine.evaluate(context, writer, "Template", template);

            if (tryToObj) {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    return mapper.readValue(writer.toString(), Object.class);
                } catch (Exception e) {
                    // 如果不是JSON，返回字符串
                    return writer.toString();
                }
            } else {
                return writer.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("Velocity模板格式化失败", e);
        }
    }

    @Override
    public FormatterType getEngineType() {
        return FormatterType.VELOCITY;
    }

    @Override
    public EngineCapability getCapability() {
        return new EngineCapability(true, true, true, true); // 全功能支持
    }
}

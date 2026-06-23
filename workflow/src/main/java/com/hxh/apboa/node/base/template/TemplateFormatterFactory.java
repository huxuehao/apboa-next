package com.hxh.apboa.node.base.template;

import com.hxh.apboa.node.base.template.impl.JacksonTemplateFormatter;
import com.hxh.apboa.node.base.template.impl.StringTemplateFormatter;
import com.hxh.apboa.node.base.template.impl.VelocityTemplateFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：模板格式化器工厂
 *
 * @author huxuehao
 **/
public class TemplateFormatterFactory {
    /**
     * 根据需求创建合适的格式化器
     */
    public static TemplateFormatter createFormatter(FormatterRequirements requirements) {
        if (requirements.isPerformanceFirst()) {
            return new StringTemplateFormatter();
        } else if (requirements.isFeatureRich()) {
            return new VelocityTemplateFormatter();
        } else {
            return new JacksonTemplateFormatter();
        }
    }

    /**
     * 根据引擎类型创建格式化器
     */
    public static TemplateFormatter createFormatter(FormatterType engineType) {
        return switch (engineType) {
            case STRING -> new StringTemplateFormatter();
            case JACKSON -> new JacksonTemplateFormatter();
            case VELOCITY -> new VelocityTemplateFormatter();
            default -> throw new IllegalArgumentException("不支持的模板引擎: " + engineType);
        };
    }

    /**
     * 获取所有可用的格式化器信息
     */
    public static List<FormatterInfo> getAvailableFormatters() {
        List<FormatterInfo> formatters = new ArrayList<>();

        formatters.add(new FormatterInfo(FormatterType.STRING, "简单变量替换", 100,
                new EngineCapability(false, false, false, false)));

        formatters.add(new FormatterInfo(FormatterType.JACKSON, "JSON模板", 80,
                new EngineCapability(false, false, false, false)));

        formatters.add(new FormatterInfo(FormatterType.VELOCITY, "全功能模板", 60,
                new EngineCapability(true, true, true, true)));

        return formatters;
    }
}

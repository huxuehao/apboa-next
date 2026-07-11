package com.hxh.apboa.node.base.template;

import lombok.Getter;

/**
 * 描述：格式化器信息
 *
 * @author huxuehao
 **/
@Getter
public class FormatterInfo {
    private FormatterType engineType;
    private String description;
    private int performanceScore; // 性能分数 (0-100)
    private EngineCapability capability;

    public FormatterInfo(FormatterType engineType, String description, int performanceScore,
                         EngineCapability capability) {
        this.engineType = engineType;
        this.description = description;
        this.performanceScore = performanceScore;
        this.capability = capability;
    }

}

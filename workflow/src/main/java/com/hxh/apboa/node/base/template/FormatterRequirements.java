package com.hxh.apboa.node.base.template;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：格式化器需求配置
 *
 * @author huxuehao
 **/
@Setter
@Getter
public class FormatterRequirements {
    private boolean performanceFirst = false; // 性能优先
    private boolean featureRich = false;      // 功能丰富
    private boolean supportExpression = false; // 支持表达式
    private boolean supportCondition = false;  // 支持条件判断
    private int estimatedQps = 100;           // 预估QPS

    public FormatterRequirements() {}

    public FormatterRequirements(boolean performanceFirst, boolean featureRich) {
        this.performanceFirst = performanceFirst;
        this.featureRich = featureRich;
    }

}

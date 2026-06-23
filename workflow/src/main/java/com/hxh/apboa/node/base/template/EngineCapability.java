package com.hxh.apboa.node.base.template;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：模板引擎能力描述
 *
 * @author huxuehao
 **/
@Setter
@Getter
public class EngineCapability {
    private boolean supportExpression = false;
    private boolean supportCondition = false;
    private boolean supportLoop = false;
    private boolean supportFunction = false;
    private long estimatedPerformance; // 预估性能分数

    public EngineCapability() {}

    public EngineCapability(boolean supportExpression, boolean supportCondition,
                            boolean supportLoop, boolean supportFunction) {
        this.supportExpression = supportExpression;
        this.supportCondition = supportCondition;
        this.supportLoop = supportLoop;
        this.supportFunction = supportFunction;
    }

}

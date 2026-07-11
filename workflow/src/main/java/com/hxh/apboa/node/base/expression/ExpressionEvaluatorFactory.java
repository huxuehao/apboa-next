package com.hxh.apboa.node.base.expression;

/**
 * 描述：表达式求值器工厂
 *
 * @author huxuehao
 **/
public class ExpressionEvaluatorFactory {
    public static ExpressionEvaluator getEvaluator() {
        return getEvaluator("GROOVY");
    }

    public static ExpressionEvaluator getEvaluator(String type) {
        return switch (type.toUpperCase()) {
            case "GROOVY" -> new GroovyExpressionEvaluator();
            //case "MVEL" -> new MvelExpressionEvaluator();
            //case "SPEL" -> new SpelExpressionEvaluator();
            default -> throw new IllegalArgumentException("不支持的表达式求值器类型: " + type);
        };
    }
}

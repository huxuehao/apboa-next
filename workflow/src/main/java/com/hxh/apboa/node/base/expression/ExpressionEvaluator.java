package com.hxh.apboa.node.base.expression;

import java.util.Map;

/**
 * 描述：表单式求值器
 *
 * @author huxuehao
 **/
public interface ExpressionEvaluator {

    /**
     * 求值表达式
     *
     * @param expression 表达式
     * @param variables  变量
     * @return 结果
     */
    Object evaluate(String expression, Map<String, Object> variables);

    /**
     * 校验表达式语法
     *
     * @param expression 表达式
     * @throws Exception 语法错误
     */
    void validateSyntax(String expression) throws Exception;
}

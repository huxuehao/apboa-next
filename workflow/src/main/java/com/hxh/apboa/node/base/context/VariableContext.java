package com.hxh.apboa.node.base.context;

import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.expression.ExpressionEvaluator;
import com.hxh.apboa.node.base.expression.ExpressionEvaluatorFactory;
import com.hxh.apboa.node.base.inputout.InputConfig;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述：变量上下文
 * 用于存储流程执行过程中的所有变量
 *
 * @author huxuehao
 **/
public class VariableContext {
    /**
     * 全局变量
     */
    private final Map<String, Object> variables = new HashMap<>();
    /**
     * 节点输出
     */
    @Getter
    private final Map<String, NodeOutput> nodeOutputs = new HashMap<>();

    /**
     * 存储变量
     */
    public void storeVariable(String variableName, Object variableValue) {
        variables.put(variableName, variableValue);
    }

    /**
     * 存储节点输出
     * 同时将节点输出的输出字段存储为变量
     * @param nodeId 节点ID
     * @param  nodeOutput 节点输出
     */
    public void storeNodeOutput(String nodeId, NodeOutput nodeOutput) {
        nodeOutputs.put(nodeId, nodeOutput);
    }

    /**
     * 获取变量值
     */
    public Object getVariable(String variableName) {
        return variables.get(variableName);
    }

    /**
     * 获取所有变量
     */
    public Map<String, Object> getAllVariables() {
        return variables;
    }

    /**
     * 解析输入
     */
    public Object resolveInput(InputConfig inputConfig) {
        return switch (inputConfig.getClassify()) {
            case CONSTANT -> evaluateConstant(inputConfig);
            case VARIABLE -> evaluateVariable(inputConfig);
            case NODE_OUTPUT -> evaluateOutput(inputConfig);
            case EXPRESSION -> evaluateExpression(inputConfig);
            default -> evaluateDefault(inputConfig);
        };
    }

    /**
     * 求值常量
     */
    private Object evaluateConstant(InputConfig inputConfig) {
        return inputConfig.getValue();
    }

    /**
     * 求值变量
     */
    private Object evaluateVariable(InputConfig inputConfig) {
        return getVariable(inputConfig.getVariableName());
    }

    /**
     * 求值其他节点输出
     */
    private Object evaluateOutput(InputConfig inputConfig) {
        NodeOutput nodeOutput = nodeOutputs.get(inputConfig.getSourceNodeId());
        return nodeOutput == null ? null : nodeOutput.getOutput(inputConfig.getSourceOutputName());
    }

    /**
     * 求值表达式
     */
    private Object evaluateExpression(InputConfig inputConfig) {
        // 获取表达式求值器
        ExpressionEvaluator evaluator = ExpressionEvaluatorFactory.getEvaluator();
        // 求值
        return evaluator.evaluate(inputConfig.getExpression(), variables);
    }

    /**
     * 默认处理
     */
    private Object evaluateDefault(InputConfig inputConfig) {
        throw new IllegalArgumentException("不支持的输入类型: " + inputConfig.getClassify());
    }
}

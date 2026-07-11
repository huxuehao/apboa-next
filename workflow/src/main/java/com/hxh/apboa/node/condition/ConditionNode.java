package com.hxh.apboa.node.condition;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.expression.ExpressionEvaluator;
import com.hxh.apboa.node.base.expression.ExpressionEvaluatorFactory;
import com.hxh.apboa.node.base.feature.BranchableNode;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

import static com.hxh.apboa.node.condition.Config.Symbol.EXPRESSION;

/**
 * 描述：条件判断节点
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class ConditionNode extends EnhancedNode implements BranchableNode {
    // 节点配置
    @Getter
    private Config config;

    public ConditionNode(String id, String name, Config config) {
        super(id, name, NodeType.IF_ELSE);
        this.config = config;
    }

    /**
     * 节点的核心执行方法
     * @param inputs  输入
     * @param context 执行上下文
     * @return 节点输出
     */
    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            // 构建执行结果
            return successNodeOutput(inputs, output, context);
        } catch (Exception e) {
            // 构建执行结果
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 成功节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs,NodeOutput output, NodeContext context) {
        boolean b;
        if (config.getSymbol() == EXPRESSION) {
            b = evaluateCondition(config.getConditionExpression(), inputs);
        } else {
            b = Evaluator.evaluate(config, inputs, context);
        }

        ExecutionResult executionResult;
        if (b) {
            executionResult = new ExecutionResult(config.getTrueNextNodeId(), true);
        } else {
            executionResult = new ExecutionResult(config.getFalseNextNodeId(), false);
        }

        // 将条件判断信息追加到执行上下文中
        if (config.getSymbol() == EXPRESSION) {
            output.addExecutionContext("conditionExpression", config.getConditionExpression());
        } else {
            output.addExecutionContext("conditionSymbol", config.getSymbol().name());
        }
        output.addExecutionContext("evaluationResult", executionResult.result());
        output.addExecutionContext("branchedToNodeId", executionResult.nextNodeId());

        context.setNextNodeId(executionResult.nextNodeId());
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, executionResult.result());
        output.markComplete();
        return output;
    }

    /**
     * 条件表达式求值
     */
    private boolean evaluateCondition(String condition, Map<String, Object> inputs) {
        Object inputValue = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        HashMap<String, Object> variables = new HashMap<>() {{
            put(NodeConst.DEFAULT_INPUT_NAME, inputValue);
        }};
        try {
            ExpressionEvaluator evaluator = ExpressionEvaluatorFactory.getEvaluator(config.getEvaluatorType());
            Object result = evaluator.evaluate(condition, variables);

            return switch (result) {
                case Boolean b -> b;
                case Number number -> number.doubleValue() != 0;
                case String s -> !s.isEmpty();
                case null, default -> result != null;
            };
        } catch (Exception e) {
            throw new RuntimeException("条件表达式求值失败: " + condition, e);
        }
    }

    /**
     * 异常节点输出
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (inputConfigs.isEmpty()) {
            return VerifyResult.invalid(new VerifyFail("input", "输入不能为空"));
        }
        if (config.getScope() == null) {
            return VerifyResult.invalid(new VerifyFail("scope", "条件分支不能为空"));
        }
        if (config.getSymbol() == null) {
            return VerifyResult.invalid(new VerifyFail("symbol", "条件运算符不能为空"));
        } else if (config.getSymbol() == EXPRESSION && FuncUtils.isEmpty(config.getConditionExpression())) {
            return VerifyResult.invalid(new VerifyFail("conditionExpression", "条件表达式不能为空"));
        }
        if (FuncUtils.isEmpty(config.getTrueNextNodeId())) {
            return VerifyResult.invalid(new VerifyFail("trueNextNodeId", "条件分支下一步节点不能为空"));
        }
        if (FuncUtils.isEmpty(config.getFalseNextNodeId())) {
            return VerifyResult.invalid(new VerifyFail("falseNextNodeId", "条件分支下一步节点不能为空"));
        }

        if (config.getCompareTo().getType() != null) {
            if (config.getCompareTo().getType() == CompareTo.Type.VARIABLE) {
                if (FuncUtils.isEmpty(config.getCompareTo().getValue())) {
                    return VerifyResult.invalid(new VerifyFail("compareTo.value", "条件分支变量值不能为空"));
                }
                if (FuncUtils.isEmpty(config.getCompareTo().getSourceNodeId())) {
                    return VerifyResult.invalid(new VerifyFail("compareTo.sourceNodeId", "条件分支变量源节点ID不能为空"));
                }
            } else {
                if (FuncUtils.isEmpty(config.getCompareTo().getValue())) {
                    return VerifyResult.invalid(new VerifyFail("compareTo.value", "条件分支常量值不能为空"));
                }
            }
        } else if (config.getSymbol() != EXPRESSION){
            return VerifyResult.invalid(new VerifyFail("compareTo", "条件分支变量不能为空"));
        }

        return VerifyResult.valid();
    }

    @Override
    public String getNextNodeId(NodeContext context) {
        if (context.getNextNodeId() == null) {
            throw new RuntimeException("未找到执行结果");
        }

        return context.getNextNodeId();
    }

}

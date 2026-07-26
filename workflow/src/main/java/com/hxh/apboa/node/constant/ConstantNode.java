package com.hxh.apboa.node.constant;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.expression.ExpressionEvaluator;
import com.hxh.apboa.node.base.expression.ExpressionEvaluatorFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述：常量节点
 * 将全局变量与输入绑定合并为变量域，对配置的表达式求值，结果作为节点输出。
 * 用于灵活加工任意上游节点的输出，如 output[0].name。
 *
 * @author huxuehao
 **/
public class ConstantNode extends EnhancedNode {
    @Getter
    private final Config config;

    public ConstantNode(String id, String name, Config config) {
        super(id, name, NodeType.CONSTANT);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output, context);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出
     * @param inputs 节点输入
     * @param output 节点输出
     * @param context 节点上下文
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        // 构造变量域：全局变量在前，输入绑定在后（同名时输入绑定优先）
        Map<String, Object> variables = new HashMap<>(context.getVariables().getAllVariables());
        variables.putAll(inputs);

        // 表达式求值
        ExpressionEvaluator evaluator = ExpressionEvaluatorFactory.getEvaluator(config.getEvaluatorType());
        Object result = evaluator.evaluate(config.getExpression(), variables);

        // 将表达式与可用变量名追加到执行上下文中
        output.addExecutionContext("expression", config.getExpression());
        output.addExecutionContext("variableNames", variables.keySet());

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, result);
        output.markComplete();
        return output;
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
        if (FuncUtils.isEmpty(config.getExpression())) {
            return VerifyResult.invalid(new VerifyFail("expression", "计算表达式不能为空"));
        }

        // 表达式语法预检
        try {
            ExpressionEvaluatorFactory.getEvaluator(config.getEvaluatorType()).validateSyntax(config.getExpression());
        } catch (Exception e) {
            return VerifyResult.invalid(new VerifyFail("expression", "表达式语法错误: " + e.getMessage()));
        }

        return VerifyResult.valid();
    }
}

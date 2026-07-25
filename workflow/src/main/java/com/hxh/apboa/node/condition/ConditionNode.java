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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        // 旧版平铺配置自动迁移为多分支结构
        if (config != null) {
            config.normalize();
        }
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
     * 成功节点输出：顺序求值各分支，首个命中即路由，全部未命中走 ELSE 兜底
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs,NodeOutput output, NodeContext context) {
        List<Branch> branches = config.getBranches();
        List<Boolean> branchResults = new ArrayList<>();
        int matchedIndex = -1;
        for (int i = 0; i < branches.size(); i++) {
            Branch branch = branches.get(i);
            boolean b;
            if (branch.getSymbol() == EXPRESSION) {
                b = evaluateCondition(branch.getConditionExpression(), inputs);
            } else {
                b = Evaluator.evaluate(branch, inputs, context);
            }
            branchResults.add(b);
            if (b) {
                matchedIndex = i;
                break;
            }
        }

        String matchedName;
        String nextNodeId;
        if (matchedIndex >= 0) {
            matchedName = branchLabel(matchedIndex, branches.get(matchedIndex));
            nextNodeId = branches.get(matchedIndex).getNextNodeId();
        } else {
            matchedName = "ELSE";
            nextNodeId = config.getElseNextNodeId();
        }

        // 将条件判断信息追加到执行上下文中
        output.addExecutionContext("branchResults", branchResults);
        output.addExecutionContext("matchedBranchIndex", matchedIndex);
        output.addExecutionContext("matchedBranchName", matchedName);
        output.addExecutionContext("branchedToNodeId", nextNodeId);

        context.setNextNodeId(nextNodeId);
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, matchedName);
        output.markComplete();
        return output;
    }

    /**
     * 分支展示名称：未命名时按位置默认为 IF / ELSE IF n
     */
    private String branchLabel(int index, Branch branch) {
        if (!FuncUtils.isEmpty(branch.getName())) {
            return branch.getName();
        }
        return index == 0 ? "IF" : "ELSE IF " + index;
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
        List<Branch> branches = config.getBranches();
        if (branches == null || branches.isEmpty()) {
            return VerifyResult.invalid(new VerifyFail("branches", "条件分支不能为空"));
        }
        for (int i = 0; i < branches.size(); i++) {
            VerifyResult result = verifyBranch(branches.get(i), "branches[" + i + "].");
            if (!result.isValid()) {
                return result;
            }
        }
        if (FuncUtils.isEmpty(config.getElseNextNodeId())) {
            return VerifyResult.invalid(new VerifyFail("elseNextNodeId", "ELSE 分支下一步节点不能为空"));
        }

        return VerifyResult.valid();
    }

    /**
     * 校验单个分支配置，错误字段带分支序号前缀便于定位
     */
    private VerifyResult verifyBranch(Branch branch, String prefix) {
        if (branch.getScope() == null) {
            return VerifyResult.invalid(new VerifyFail(prefix + "scope", "条件分支不能为空"));
        }
        if (branch.getSymbol() == null) {
            return VerifyResult.invalid(new VerifyFail(prefix + "symbol", "条件运算符不能为空"));
        } else if (branch.getSymbol() == EXPRESSION && FuncUtils.isEmpty(branch.getConditionExpression())) {
            return VerifyResult.invalid(new VerifyFail(prefix + "conditionExpression", "条件表达式不能为空"));
        }
        if (FuncUtils.isEmpty(branch.getNextNodeId())) {
            return VerifyResult.invalid(new VerifyFail(prefix + "nextNodeId", "条件分支下一步节点不能为空"));
        }

        CompareTo compareTo = branch.getCompareTo();
        if (compareTo != null && compareTo.getType() != null) {
            if (compareTo.getType() == CompareTo.Type.VARIABLE) {
                if (FuncUtils.isEmpty(compareTo.getValue())) {
                    return VerifyResult.invalid(new VerifyFail(prefix + "compareTo.value", "条件分支变量值不能为空"));
                }
                if (FuncUtils.isEmpty(compareTo.getSourceNodeId())) {
                    return VerifyResult.invalid(new VerifyFail(prefix + "compareTo.sourceNodeId", "条件分支变量源节点ID不能为空"));
                }
            } else {
                if (FuncUtils.isEmpty(compareTo.getValue())) {
                    return VerifyResult.invalid(new VerifyFail(prefix + "compareTo.value", "条件分支常量值不能为空"));
                }
            }
        } else if (branch.getSymbol() != EXPRESSION){
            return VerifyResult.invalid(new VerifyFail(prefix + "compareTo", "条件分支变量不能为空"));
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

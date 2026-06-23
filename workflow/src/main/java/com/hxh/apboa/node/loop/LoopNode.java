package com.hxh.apboa.node.loop;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.expression.ExpressionEvaluator;
import com.hxh.apboa.node.base.expression.ExpressionEvaluatorFactory;
import com.hxh.apboa.node.base.feature.LoopableNode;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.*;

/**
 * 描述：循环节点
 * 支持基于数据集合的迭代循环和固定次数循环。
 * 每次迭代执行一个子工作流，子工作流中的节点可以访问当前迭代索引和迭代元素。
 * 支持通过 Groovy 表达式提前终止循环。
 *
 * @author huxuehao
 **/
public class LoopNode extends EnhancedNode implements LoopableNode {
    @Getter
    private final Config config;
    /** 缓存的表达式执行器，避免每次迭代都重新创建 */
    private final ExpressionEvaluator evaluator;

    public LoopNode(String id, String name, Config config) {
        super(id, name, NodeType.LOOP);
        this.config = config;
        this.evaluator = ExpressionEvaluatorFactory.getEvaluator();
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
     *
     * @param inputs  节点输入
     * @param output  节点输出
     * @param context 执行上下文
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        // 获取迭代数据
        Iterable<?> iterable = resolveIterateData(inputs, context);

        // 执行循环
        List<Object> iterationResults = new ArrayList<>();
        int index = 0;

        if (iterable != null) {
            // 基于数据集合的迭代循环
            for (Object item : iterable) {
                if (index >= config.getMaxIterations()) {
                    break;
                }

                // 检查终止条件
                if (shouldTerminate(index, item, context)) {
                    break;
                }

                // 执行一次迭代
                Object iterationResult = executeIteration(index, item, context);
                iterationResults.add(iterationResult);
                index++;
            }
        } else {
            // 纯计数循环
            while (index < config.getMaxIterations()) {
                // 检查终止条件
                if (shouldTerminate(index, null, context)) {
                    break;
                }

                // 执行一次迭代
                Object iterationResult = executeIteration(index, null, context);
                iterationResults.add(iterationResult);
                index++;
            }
        }

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, iterationResults);
        output.addOutput("totalIterations", index);
        output.markComplete();
        return output;
    }

    /**
     * 解析迭代数据源
     *
     * @param inputs  节点输入
     * @param context 执行上下文
     * @return 迭代数据集合，null 表示使用纯计数循环
     */
    private Iterable<?> resolveIterateData(Map<String, Object> inputs, NodeContext context) {
        String dataSource = config.getIterateDataSource();
        if (FuncUtils.isEmpty(dataSource)) {
            return null;
        }

        // 先从节点输入中查找
        Object data = inputs.get(dataSource);
        if (data == null) {
            // 再从全局变量中查找
            data = context.getVariables().getVariable(dataSource);
        }

        if (data == null) {
            throw new RuntimeException("迭代数据源为空: " + dataSource);
        }

        if (data instanceof Iterable<?> iterable) {
            return iterable;
        }

        if (data.getClass().isArray()) {
            return Arrays.asList((Object[]) data);
        }

        throw new RuntimeException("迭代数据源不是可迭代类型: " + dataSource);
    }

    /**
     * 检查循环终止条件
     *
     * @param index  当前迭代索引
     * @param item   当前迭代元素（计数循环时为 null）
     * @param context 执行上下文
     * @return true 表示需要终止循环
     */
    private boolean shouldTerminate(int index, Object item, NodeContext context) {
        String expression = config.getTerminationExpression();
        if (FuncUtils.isEmpty(expression)) {
            return false;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put(config.getLoopVariable(), index);
        if (item != null) {
            variables.put(config.getItemVariable(), item);
        }
        // 注入全局变量
        variables.putAll(context.getVariables().getAllVariables());

        try {
            Object result = evaluator.evaluate(expression, variables);

            return switch (result) {
                case Boolean b -> b;
                case Number number -> number.doubleValue() != 0;
                case String s -> !s.isEmpty();
                case null, default -> result != null;
            };
        } catch (Exception e) {
            throw new RuntimeException("循环终止条件表达式求值失败: " + expression, e);
        }
    }

    /**
     * 执行单次迭代
     *
     * @param index   当前迭代索引
     * @param item    当前迭代元素（计数循环时为 null）
     * @param context 父工作流的执行上下文
     * @return 本次迭代的输出结果
     */
    private Object executeIteration(int index, Object item, NodeContext context) {
        // 设置循环变量到上下文
        context.getVariables().storeVariable(config.getLoopVariable(), index);
        if (item != null) {
            context.getVariables().storeVariable(config.getItemVariable(), item);
        }

        if (config.getWorkflow() == null) {
            throw new RuntimeException("循环节点未配置工作流实例");
        }

        // 执行子工作流
        return config.getWorkflow().executeSubWorkflow(
                config.getSubNodes(),
                config.getSubEdges(),
                config.getEntryNodeId(),
                context.getVariables()
        );
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
        if (config.getMaxIterations() <= 0) {
            return VerifyResult.invalid(new VerifyFail("maxIterations", "最大迭代次数必须大于0"));
        }
        if (config.getSubNodes() == null || config.getSubNodes().isEmpty()) {
            return VerifyResult.invalid(new VerifyFail("subNodes", "子工作流节点列表不能为空"));
        }
        if (config.getSubEdges() == null || config.getSubEdges().isEmpty()) {
            return VerifyResult.invalid(new VerifyFail("subEdges", "子工作流边列表不能为空"));
        }
        if (FuncUtils.isEmpty(config.getEntryNodeId())) {
            return VerifyResult.invalid(new VerifyFail("entryNodeId", "子工作流入口节点ID不能为空"));
        }
        if (config.getWorkflow() == null) {
            return VerifyResult.invalid(new VerifyFail("workflow", "工作流实例不能为空"));
        }
        if (FuncUtils.isEmpty(config.getLoopVariable())) {
            return VerifyResult.invalid(new VerifyFail("loopVariable", "循环变量名不能为空"));
        }

        return VerifyResult.valid();
    }
}

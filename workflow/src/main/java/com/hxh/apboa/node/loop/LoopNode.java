package com.hxh.apboa.node.loop;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.expression.ExpressionEvaluator;
import com.hxh.apboa.node.base.expression.ExpressionEvaluatorFactory;
import com.hxh.apboa.node.base.feature.LoopableNode;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.workflow.core.Edge;
import com.fasterxml.jackson.databind.JsonNode;
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
    /** 缓存的表达式执行器 */
    private final ExpressionEvaluator evaluator;
    /** 编译后的子工作流节点（首次迭代时编译，后续复用） */
    private List<Node> compiledSubNodes;
    /** 编译后的子工作流边 */
    private List<Edge> compiledSubEdges;

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

    private void ensureCompiled() {
        if (compiledSubNodes != null && compiledSubEdges != null) {
            return;
        }
        LoopSubWorkflowCompiler compiler = SpringContextHolder.getBean(LoopSubWorkflowCompiler.class);
        compiledSubNodes = compiler.compileNodes(config.getSubNodes());
        compiledSubEdges = compiler.compileEdges(config.getSubEdges());
    }

    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        ensureCompiled();

        Iterable<?> iterable = resolveIterateData(inputs, context);
        List<Object> iterationResults = new ArrayList<>();
        int index = 0;

        if (iterable != null) {
            for (Object item : iterable) {
                if (index >= config.getMaxIterations()) break;
                if (shouldTerminate(index, item, context)) break;
                iterationResults.add(executeIteration(index, item, context));
                index++;
            }
        } else {
            while (index < config.getMaxIterations()) {
                if (shouldTerminate(index, null, context)) break;
                iterationResults.add(executeIteration(index, null, context));
                index++;
            }
        }

        output.addExecutionContext("totalIterations", index);
        output.addExecutionContext("loopVariable", config.getLoopVariable());
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, iterationResults);
        output.addOutput("totalIterations", index);
        output.markComplete();
        return output;
    }

    private Iterable<?> resolveIterateData(Map<String, Object> inputs, NodeContext context) {
        String dataSource = config.getIterateDataSource();
        if (FuncUtils.isEmpty(dataSource)) return null;

        Object data = inputs.get(dataSource);
        if (data == null) data = context.getVariables().getVariable(dataSource);
        if (data == null) throw new RuntimeException("迭代数据源为空: " + dataSource);
        if (data instanceof Iterable<?> iterable) return iterable;
        if (data.getClass().isArray()) return Arrays.asList((Object[]) data);
        throw new RuntimeException("迭代数据源不是可迭代类型: " + dataSource);
    }

    private boolean shouldTerminate(int index, Object item, NodeContext context) {
        String expression = config.getTerminationExpression();
        if (FuncUtils.isEmpty(expression)) return false;

        Map<String, Object> variables = new HashMap<>();
        variables.put(config.getLoopVariable(), index);
        if (item != null) variables.put(config.getItemVariable(), item);
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

    private Object executeIteration(int index, Object item, NodeContext context) {
        context.getVariables().storeVariable(config.getLoopVariable(), index);
        if (item != null) context.getVariables().storeVariable(config.getItemVariable(), item);
        if (config.getWorkflow() == null) throw new RuntimeException("循环节点未配置工作流实例");

        return config.getWorkflow().executeSubWorkflow(
                compiledSubNodes, compiledSubEdges,
                config.getEntryNodeId(), context.getVariables());
    }

    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (config.getMaxIterations() <= 0)
            return VerifyResult.invalid(new VerifyFail("maxIterations", "最大迭代次数必须大于0"));
        JsonNode sn = config.getSubNodes();
        if (sn == null || !sn.isArray() || sn.isEmpty())
            return VerifyResult.invalid(new VerifyFail("subNodes", "子工作流节点列表不能为空"));
        JsonNode se = config.getSubEdges();
        if (se == null || !se.isArray() || se.isEmpty())
            return VerifyResult.invalid(new VerifyFail("subEdges", "子工作流边列表不能为空"));
        if (FuncUtils.isEmpty(config.getEntryNodeId()))
            return VerifyResult.invalid(new VerifyFail("entryNodeId", "子工作流入口节点ID不能为空"));
        if (FuncUtils.isEmpty(config.getLoopVariable()))
            return VerifyResult.invalid(new VerifyFail("loopVariable", "循环变量名不能为空"));
        return VerifyResult.valid();
    }
}

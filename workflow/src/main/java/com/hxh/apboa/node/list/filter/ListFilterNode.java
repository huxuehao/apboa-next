package com.hxh.apboa.node.list.filter;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.expression.ExpressionEvaluator;
import com.hxh.apboa.node.base.expression.ExpressionEvaluatorFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 描述：列表过滤节点
 *
 * @author huxuehao
 **/
public class ListFilterNode extends EnhancedNode {
    @Getter
    private final Config config;
    private static final String ITEM_NAME = "item";

    public ListFilterNode(String id, String name, Config config) {
        super(id, name, NodeType.LIST_FILTER);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output, context);
        } catch (Exception e) {
            return executionNodeOutput(e,  output);
        }
    }

    /**
     * 创建成功输出
     * @param inputs 节点输入
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output, NodeContext context) throws Exception {
        Object input = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        Iterable<?> iterable = (Iterable<?>) input;

        switch (config.getMode()) {
            case SIMPLE -> simpleNodeOutput(iterable, output, context);
            case EXPRESSION -> expressionNodeOutput(iterable, output);
        }

        output.markComplete();
        return output;
    }

    /**
     * 简单模式节点输出
     * @param iterable 节点输入
     * @param output   节点输出
     */
    private void simpleNodeOutput(Iterable<?> iterable, NodeOutput output, NodeContext context) throws Exception {
        List<Object> outputList = new LinkedList<>();

        for (Object item : iterable) {
            if (SimpleEvaluator.evaluate(getItemValue(item), config, context)) {
                outputList.add(item);
            }
        }

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, outputList);
    }

    /**
     * 基于表达式获取元素的实际值
     */
    private Object getItemValue(Object item) {
        String condition = config.getCondition();
        // 如果是属性访问，如 "item.age"
        if (condition.startsWith(ITEM_NAME + ".")) {
            // 去掉 "item."
            String property = condition.substring(5);
            return getPropertyValue(item, property);
        }

        return item;
    }

    /**
     * 获取对象属性值
     */
    private Object getPropertyValue(Object item, String property) {
        try {
            if (item instanceof Map) {
                return ((Map<?, ?>) item).get(property);
            } else {
                // 使用反射获取属性值
                Field field = item.getClass().getDeclaredField(property);
                field.setAccessible(true);
                return field.get(item);
            }
        } catch (Exception e) {
            throw new RuntimeException("无法获取属性值: " + property, e);
        }
    }

    /**
     * 表达式节点输出
     * @param iterable 节点输入
     * @param output   节点输出
     */
    private void expressionNodeOutput(Iterable<?> iterable, NodeOutput output) throws Exception {
        List<Object> outputList = new LinkedList<>();
        ExpressionEvaluator evaluator = ExpressionEvaluatorFactory.getEvaluator(config.getEvaluatorType());

        // 遍历处理元素获取符合条件的元素
        Map<String, Object> variable = new HashMap<>();
        for (Object item : iterable) {
            try {
                variable.put(ITEM_NAME, item);
                if ((boolean) evaluator.evaluate(config.getCondition(), variable)) {
                    outputList.add(item);
                }
            } catch (Exception e) {
                throw new Exception("处理元素时发生错误: " + e.getMessage(), e);
            }
        }

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, outputList);
    }


    /**
     * 异常节点输出
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed( getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        Object o = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        if (!(o instanceof Iterable<?>)) {
            return VerifyResult.invalid(new VerifyFail("input", "输入数据必须是可迭代对象"));
        }

        if (FuncUtils.isEmpty( config.getCondition())) {
            return VerifyResult.invalid(new VerifyFail("condition", "条件不能为空"));
        }

        return VerifyResult.valid();
    }
}

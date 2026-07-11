package com.hxh.apboa.node.list.sort;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.expression.ExpressionEvaluator;
import com.hxh.apboa.node.base.expression.ExpressionEvaluatorFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 描述：列表排序节点
 * 只支持整数排序，所谓的整数排序是指无论是使用 item 、item.age还是GROOVY表达式，其结果必须是整数，否则无法进行排序
 *
 * @author huxuehao
 **/
public class ListSortNode extends EnhancedNode {
    @Getter
    private final Config config;
    private static final String ITEM_NAME = "item";

    public ListSortNode(String id, String name, Config config) {
        super(id, name, NodeType.LIST_SORT);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output);
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
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) {
        Object input = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        Iterable<?> iterable = (Iterable<?>) input;
        List<Object> inputList = new ArrayList<>();
        for (Object item : iterable) {
            inputList.add(item);
        }

        // 创建比较器
        Comparator<Object> comparator = Comparator.comparingInt((o) -> computeSortKey(o, config.getCondition()));
        // 排序
        if (config.getDirection() == SortDirection.ASC) {
            inputList.sort(comparator);
        } else {
            inputList.sort(comparator.reversed());
        }

        // 将列表排序信息追加到执行上下文中
        output.addExecutionContext("sortExpression", config.getCondition());
        output.addExecutionContext("sortDirection", config.getDirection().name());
        output.addExecutionContext("inputCount", inputList.size());

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, inputList);
        output.markComplete();
        return output;
    }


    /**
     * 计算元素的排序键
     */
    private Integer computeSortKey(Object item, String sortBy) {
        // 如果待排序的元素为空，将直接尝试使用元素本身
        // 至于是允许列表中包含空元素，还是不允许由 toInteger 函数决定
        if (FuncUtils.isEmpty(item)) {
            return toInteger(item);
        }
        // 如果 sortBy 为空或 "item"，直接使用元素本身
        if (ITEM_NAME.equals(sortBy.trim())) {
            return toInteger(item);
        }

        // 如果是属性访问，如 "item.age"
        if (sortBy.startsWith(ITEM_NAME + ".")) {
            String property = sortBy.substring(5); // 去掉 "item."
            return getPropertyValue(item, property);
        }

        // 使用 Groovy 表达式计算排序键
        return evaluateGroovyExpression(item, sortBy);
    }

    /**
     * 获取对象属性值
     */
    private Integer getPropertyValue(Object item, String property) {
        try {
            if (item instanceof Map) {
                Object value = ((Map<?, ?>) item).get(property);
                return toInteger(value);
            } else {
                // 使用反射获取属性值
                Field field = item.getClass().getDeclaredField(property);
                field.setAccessible(true);
                Object value = field.get(item);
                return toInteger(value);
            }
        } catch (Exception e) {
            throw new RuntimeException("无法获取属性值: " + property, e);
        }
    }

    /**
     * 执行 Groovy 表达式
     */
    private Integer evaluateGroovyExpression(Object item, String expression) {
        Map<String, Object> variable = new HashMap<>() {{
            put(ITEM_NAME, item);
        }};
        ExpressionEvaluator evaluator = ExpressionEvaluatorFactory.getEvaluator(config.getEvaluatorType());
        return toInteger(evaluator.evaluate(expression, variable));
    }

    /**
     * 安全地将对象转换为整数
     */
    private Integer toInteger(Object value) {
        switch (value) {
            case Integer i -> {
                return i;
            }
            case Number number -> {
                return number.intValue();
            }
            case Boolean b -> {
                return b ? 1 : 0;
            }
            case String s -> {
                return Integer.parseInt(s);
            }
            default -> {
                // 判断遇到非法元素时是继续处理还是抛出异常
                encounteringIllegalElements();
                if (config.getNullFirst()) {
                    if (config.getDirection() == SortDirection.ASC) {
                        return Integer.MIN_VALUE;
                    } else {
                        return Integer.MAX_VALUE;
                    }
                } else {
                    if (config.getDirection() == SortDirection.ASC) {
                        return Integer.MAX_VALUE;
                    } else {
                        return Integer.MIN_VALUE;
                    }
                }
            }
        }
    }

    /**
     * 遇到非法元素时，判断是继续进行还是抛出异常
     */
    private void encounteringIllegalElements() {
        if (config.getStrictMode()) {
            throw new RuntimeException("在严格模式下遇到非法元素，无法继续进行排序操作");
        }
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

        if (config.getDirection() == null) {
            return VerifyResult.invalid(new VerifyFail("direction", "排序方向不能为空"));
        }

        if (FuncUtils.isEmpty( config.getEvaluatorType())) {
            return VerifyResult.invalid(new VerifyFail("evaluatorType", "表达式求值器类型不能为空"));
        }

        if (FuncUtils.isEmpty(config.getCondition())) {
            return VerifyResult.invalid(new VerifyFail("condition", "排序表达式不能为空"));
        }

        return VerifyResult.valid();
    }
}

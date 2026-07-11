package com.hxh.apboa.node.variable.agg;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.*;

/**
 * 描述：变量聚合
 *
 * @author huxuehao
 **/
public class VariableAggNode extends EnhancedNode {
    @Getter
    private final Config config;

    public VariableAggNode(String id, String name, Config config) {
        super(id, name, NodeType.VARIABLE_AGG);
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
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) throws Exception {
        switch (config.getStrategy()) {
            case MAP -> aggMap(inputs, output);
            case ARRAY -> aggArray(inputs, output);
            case STRING -> aggString(inputs, output);
        }

        // 将变量聚合信息追加到执行上下文中
        output.addExecutionContext("aggStrategy", config.getStrategy().name());
        output.addExecutionContext("excludeNull", config.isExcludeNull());

        output.markComplete();
        return output;
    }

    /**
     * 聚合成Map
     */
    private void aggMap(Map<String, Object> inputs, NodeOutput output) {
        if (config.isExcludeNull()) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                if (entry.getValue() != null) {
                    map.put(entry.getKey(), entry.getValue());
                }
            }
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, map);
        } else {
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, inputs);
        }
    }

    /**
     * 聚合成Array
     */
    private void aggArray(Map<String, Object> inputs, NodeOutput output) {
        Collection<Object> values = inputs.values();
        List<Object> result;
        if (config.isExcludeNull()) {
            result = values.stream().filter(Objects::nonNull).toList();
        } else {
            result = new ArrayList<>(values);
        }
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, result);
    }

    /**
     * 聚合成字符串
     */
    private void aggString(Map<String, Object> inputs, NodeOutput output) {
        // 拼接字符串
        StringJoiner joiner = new StringJoiner(config.getSplicingSymbol());
        inputs.forEach((k, v) -> {
            if (v != null) {
                joiner.add(v.toString());
            } else if (!config.isExcludeNull()){
                joiner.add("");
            }
        });

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, joiner.toString());
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
        if (FuncUtils.isEmpty(config.getStrategy())) {
            return VerifyResult.invalid(new VerifyFail("strategy", "选择策略不能为空"));
        }

        // 获取输入参数类型
        Set<String> inputValueTypes = new HashSet<>();
        inputs.forEach((k, v) -> {
            if (v != null) {
               inputValueTypes.add(v.getClass().getSimpleName());
            }
        });

        // 判断输入参数类型一致性
        if (inputValueTypes.size() > 1) {
            return VerifyResult.invalid(new VerifyFail("inputs", "输入参数类型不一致"));
        }

        return VerifyResult.valid();
    }
}

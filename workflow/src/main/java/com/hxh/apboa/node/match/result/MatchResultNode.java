package com.hxh.apboa.node.match.result;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.inputout.InputConfig;
import com.hxh.apboa.node.base.inputout.OutputConfig;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * 描述：匹配结果节点
 *
 * @author huxuehao
 **/
public class MatchResultNode extends EnhancedNode {
    @Getter
    private final Config config;

    public MatchResultNode(String id, String name, Config config) {
        super(id, name, NodeType.MATCH_RESULT);
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
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        String nextId = evaluateCase(inputs);
        context.setNextNodeId(nextId);

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, nextId);
        output.markComplete();
        return output;
    }

    private String evaluateCase(Map<String, Object> inputs) {
        switch (config.getMatchType()) {
            case EQUALS -> {
                return evaluateEquals(inputs);
            }
            case CONTAINS -> {
                return evaluateContains(inputs);
            }
            default -> throw new RuntimeException("不支持的匹配类型");
        }
    }

    private String evaluateEquals(Map<String, Object> inputs) {
        Object o = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        if (o == null) {
            return config.getDefaultNextNodeId();
        }
        OutputConfig.VariableType inputType = getInputTypeByName(NodeConst.DEFAULT_INPUT_NAME);
        return switch (inputType) {
            case String, Long, Integer, Float, Double, Boolean -> {
                if (config.isCaseSensitive()) {
                    for (MatchConfig match : config.getMatches()) {
                        if (o.toString().equals(match.getMatchValue())) {
                            yield match.getNextNodeId();
                        }
                    }
                } else {
                    for (MatchConfig match : config.getMatches()) {
                        if (o.toString().equalsIgnoreCase(match.getMatchValue())) {
                            yield match.getNextNodeId();
                        }
                    }
                }
                yield config.getDefaultNextNodeId();
            }
            default -> throw new RuntimeException(config.getMatchType() + "规则不支持的输入类型" + inputType);
        };
    }


    private String evaluateContains(Map<String, Object> inputs) {
        Object input = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        if (input == null) {
            return config.getDefaultNextNodeId();
        }
        OutputConfig.VariableType inputType = getInputTypeByName(NodeConst.DEFAULT_INPUT_NAME);
        return switch (inputType) {
            case String -> {
                // 区分大小写
                if (config.isCaseSensitive()) {
                    for (MatchConfig match : config.getMatches()) {
                        if (input.toString().contains(match.getMatchValue())) {
                            yield match.getNextNodeId();
                        }
                    }
                } else {
                    for (MatchConfig match : config.getMatches()) {
                        if (match.getMatchValue() != null
                                && input.toString().toLowerCase().contains(match.getMatchValue().toLowerCase())) {
                            yield match.getNextNodeId();
                        }
                    }
                }
                yield config.getDefaultNextNodeId();
            }
            case Array -> {
                // 输入值转迭代器
                Collection<?> inputCollection = (Collection<?>) input;
                Iterator<?> iterator = inputCollection.iterator();
                for (MatchConfig match : config.getMatches()) {
                    while (iterator.hasNext()) {
                        Object item = iterator.next();
                        if (item == null) {
                            continue;
                        }
                        // 区分大小写
                        if (config.isCaseSensitive()) {
                            if (item.toString().contains(match.getMatchValue())) {
                                yield match.getNextNodeId();
                            }
                        } else {
                            if (match.getMatchValue() != null
                                    && item.toString().toLowerCase().contains(match.getMatchValue().toLowerCase())) {
                                yield match.getNextNodeId();
                            }
                        }
                    }
                }
                yield config.getDefaultNextNodeId();
            }
            default -> throw new RuntimeException(config.getMatchType() + "规则不支持的输入类型" + inputType);
        };
    }

    private OutputConfig.VariableType getInputTypeByName(String name) {
        for (InputConfig inputConfig : inputConfigs) {
            if (inputConfig.getName().equals(name)) {
                return inputConfig.getType();
            }
        }
        throw new RuntimeException("未找到名称为"+name+"的输入配置");
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
        if (FuncUtils.isEmpty(config.getMatches())) {
            return VerifyResult.invalid(new VerifyFail("match", "匹配条件不能为空"));
        }

        for (MatchConfig match : config.getMatches()) {
            if (FuncUtils.isEmpty(match.getNextNodeId())) {
                return VerifyResult.invalid(new VerifyFail("match.nextNodeId", "匹配条件下一步节点不能为空"));
            }
        }

        return VerifyResult.valid();
    }

    @Override
    public String getNextNodeId(NodeContext context) {
        return context.getNextNodeId();
    }
}

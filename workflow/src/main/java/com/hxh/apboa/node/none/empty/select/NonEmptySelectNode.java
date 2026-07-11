package com.hxh.apboa.node.none.empty.select;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.inputout.InputConfig;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 描述：非空选择节点
 *
 * @author huxuehao
 **/
public class NonEmptySelectNode extends EnhancedNode {
    @Getter
    private final Config config;

    public NonEmptySelectNode(String id, String name, Config config) {
        super(id, name, NodeType.NON_EMPTY_SELECT);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(output, context);
        } catch (Exception e) {
            return executionNodeOutput(e,  output);
        }
    }

    /**
     * 创建成功输出
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(NodeOutput output, NodeContext context) throws Exception {
        switch (config.getStrategy()) {
            case FIRST -> selectFirst(output, context);
            case LAST -> selectLast(output, context);
        }

        output.markComplete();
        return output;
    }

    /**
     * 选择第一个
     */
    private void selectFirst(NodeOutput output, NodeContext context) {
        List<InputConfig> improveInputs = getInputConfigs();
        for (InputConfig improveInput : improveInputs) {
            Object value = context.getVariables().resolveInput(improveInput);
            if (FuncUtils.isNotEmpty(value)) {
                output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, value);
                return;
            }
        }

        for (InputConfig improveInput : improveInputs) {
            if (improveInput.getSourceNodeId().equals(config.getDefaultNextNodeId())) {
                Object value = context.getVariables().resolveInput(improveInput);
                output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, value);
            }
        }
    }

    /**
     * 选择最后一个
     */
    private void selectLast(NodeOutput output, NodeContext context) {
        List<InputConfig> improveInputs = getInputConfigs();
        for (InputConfig improveInput : improveInputs) {
            Object value = context.getVariables().resolveInput(improveInput);
            if (FuncUtils.isNotEmpty(value)) {
                output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, value);
            }
        }

        if (!output.hasOutput(NodeConst.DEFAULT_OUTPUT_NAME)) {
            for (InputConfig improveInput : improveInputs) {
                if (improveInput.getSourceNodeId().equals(config.getDefaultNextNodeId())) {
                    Object value = context.getVariables().resolveInput(improveInput);
                    output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, value);
                }
            }
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
        if (FuncUtils.isEmpty(config.getStrategy())) {
            return VerifyResult.invalid(new VerifyFail("strategy", "选择策略不能为空"));
        }

        return VerifyResult.valid();
    }
}

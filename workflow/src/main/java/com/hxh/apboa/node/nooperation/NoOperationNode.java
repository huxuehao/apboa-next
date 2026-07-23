package com.hxh.apboa.node.nooperation;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.Map;

/**
 * 空操作节点，不执行任何操作，仅作为桥接节点使用
 *
 * @author huxuehao
 */
public class NoOperationNode extends EnhancedNode {

    @Getter
    private final Config config;

    public NoOperationNode(String id, String name, Config config) {
        super(id, name, NodeType.NO_OPERATION);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, true);
        output.markComplete();
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        return VerifyResult.valid();
    }
}

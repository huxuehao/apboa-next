package com.hxh.apboa.node.toolexecute;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.Map;

/**
 * 工具执行节点。
 * 调用平台已注册的内置或自定义工具，将输入绑定参数传递给工具并返回执行结果。
 *
 * @author huxuehao
 */
public class ToolExecuteNode extends EnhancedNode {
    @Getter
    private final Config config;

    public ToolExecuteNode(String id, String name, Config config) {
        super(id, name, NodeType.TOOL_EXECUTE);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出。
     *
     * @param inputs 节点输入
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) {
        ToolNodeExecutor executor = SpringContextHolder.getBean(ToolNodeExecutor.class);
        Object result = executor.execute(config.getToolId(), inputs);

        output.addExecutionContext("toolId", config.getToolId());
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, result);
        output.markComplete();
        return output;
    }

    /**
     * 创建异常输出。
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (config.getToolId() == null) {
            return VerifyResult.invalid(new VerifyFail("toolId", "工具ID不能为空"));
        }
        return VerifyResult.valid();
    }
}

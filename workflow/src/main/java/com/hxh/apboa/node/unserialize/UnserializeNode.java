package com.hxh.apboa.node.unserialize;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.node.unserialize.unserializer.*;
import lombok.Getter;

import java.util.Map;

/**
 * 描述：反序列化节点
 *
 * @author huxuehao
 **/
public class UnserializeNode extends EnhancedNode {
    @Getter
    private final Config config;

    public UnserializeNode(String id, String name, Config config) {
        super(id, name, NodeType.UNSERIALIZE);
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
        Object input = inputs.get(NodeConst.DEFAULT_INPUT_NAME);

        if (!(input instanceof String)) {
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, input);
        } else {
            Object result = matchSerializeMode(input.toString());
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, result);
        }

        output.markComplete();
        return output;
    }

    // 匹配序列化模式
    private Object matchSerializeMode(String input) throws Exception {
        return switch (config.getFormat()) {
            case BASE64 -> new Base64UnSerializer().unserialize(input, config);
            case JSON -> new JsonUnSerializer().unserialize(input, config);
            case URL_ENCODED -> new UrlEncodeUnSerializer().unserialize(input, config);
            case XML -> new XmlUnSerializer().unserialize(input, config);
            case YAML -> new YamlUnSerializer().unserialize(input, config);
            default -> throw new IllegalArgumentException("不支持的序列化模式: " + config.getFormat());
        };
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
        if (config.getFormat() == null) {
            return VerifyResult.invalid(new VerifyFail("format", "反序列化格式不能为空"));
        }
        return VerifyResult.valid();
    }
}

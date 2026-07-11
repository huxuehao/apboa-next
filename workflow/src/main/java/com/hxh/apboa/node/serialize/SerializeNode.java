package com.hxh.apboa.node.serialize;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.node.serialize.serializer.*;
import lombok.Getter;

import java.util.Map;

/**
 * 描述：序列化节点
 *
 * @author huxuehao
 **/
public class SerializeNode extends EnhancedNode {
    @Getter
    private Config config;

    public SerializeNode(String id, String name, Config config) {
        super(id, name, NodeType.SERIALIZE);
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
        String result = matchSerializeMode(input);

        // 将序列化信息追加到执行上下文中
        output.addExecutionContext("serializeFormat", config.getFormat().name());

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, result);
        output.markComplete();
        return output;
    }

    // 匹配序列化模式
    private String matchSerializeMode(Object input) throws Exception {
        return switch (config.getFormat()) {
            case BASE64 -> new Base64Serializer().serialize(input, config);
            case JSON -> new JsonSerializer().serialize(input, config);
            case URL_ENCODED -> new UrlEncodeSerializer().serialize(input, config);
            case XML -> new XmlSerializer().serialize(input, config);
            case YAML -> new YamlSerializer().serialize(input, config);
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
            return VerifyResult.invalid(new VerifyFail("format", "序列化格式不能为空"));
        }
        return VerifyResult.valid();
    }
}

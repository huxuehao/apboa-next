package com.hxh.apboa.node.end;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.feature.EndableNode;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.Map;

/**
 * 描述：结束节点
 *
 * @author huxuehao
 **/
public class EndNode extends EnhancedNode implements EndableNode {
    @Getter
    private final Config config;

    public EndNode(String id, String name, Config config) {
        super(id, name, NodeType.END);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(output, inputs);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出
     * @param output 节点输出
     * @param variables 变量
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(NodeOutput output, Map<String, Object> variables) {
        TemplateFormatter formatter = TemplateFormatterFactory.createFormatter(config.getFormatterType());
        Object formatRes = formatter.format(config.getResponseTemplate(), variables);
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, formatRes);
        output.markComplete();
        return output;
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
        if (FuncUtils.isEmpty(config.getFormatterType())) {
            return VerifyResult.invalid(new VerifyFail("formatterType", "格式化类型不能为空"));
        }
        return VerifyResult.valid();
    }
}

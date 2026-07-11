package com.hxh.apboa.node.string.template;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.template.FormatterType;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 描述：字符串模板节点
 *
 * @author huxuehao
 **/
public class StringTemplateNode extends EnhancedNode {
    @Getter
    private Config config;

    public StringTemplateNode(String id, String name, Config config) {
        super(id, name, NodeType.STRING_TEMPLATE);
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
        if (inputs == null || inputs.isEmpty()) {
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, config.getTemplate());
        } else {
            TemplateFormatter formatter = TemplateFormatterFactory.createFormatter(config.getTemplateType());
            Object formatRes = formatter.format(config.getTemplate(), inputs, false);
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, formatRes.toString());
        }

        // 将字符串模板信息追加到执行上下文中
        output.addExecutionContext("templateType", config.getTemplateType().name());
        output.addExecutionContext("template", config.getTemplate());

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
        if (FuncUtils.isEmpty(config.getTemplateType())) {
            return VerifyResult.invalid(new VerifyFail("type", "类型不能为空"));
        }
        if (!List.of(FormatterType.STRING, FormatterType.VELOCITY).contains(config.getTemplateType())) {
            return VerifyResult.invalid(new VerifyFail("type", "不支持的模版类型：" + config.getTemplateType()));
        }
        return VerifyResult.valid();
    }
}

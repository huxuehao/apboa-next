package com.hxh.apboa.node.channel.email;

import com.hxh.apboa.channel.entity.Channel;
import com.hxh.apboa.channel.mapper.ChannelMapper;
import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.node.channel.sender.ChannelSender;
import com.hxh.apboa.node.channel.sender.EmailSender;
import com.hxh.apboa.node.channel.sender.MessageParams;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 发送邮件节点
 *
 * @author huxuehao
 */
public class EmailSendNode extends EnhancedNode {

    @Getter
    private final Config config;
    private final TemplateFormatter formatter;

    public EmailSendNode(String id, String name, Config config) {
        super(id, name, NodeType.EMAIL_SEND);
        this.config = config;
        this.formatter = TemplateFormatterFactory.createFormatter(config.getFormatterType());
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) throws Exception {
        // 获取渠道配置
        ChannelMapper mapper = SpringContextHolder.getBean(ChannelMapper.class);
        Channel channel = mapper.selectById(config.getChannelId());
        if (channel == null) {
            throw new RuntimeException("渠道配置不存在，ID: " + config.getChannelId());
        }
        if (!Boolean.TRUE.equals(channel.getEnabled())) {
            throw new RuntimeException("渠道未启用，ID: " + config.getChannelId());
        }

        // 渲染模板
        String resolvedSubject = resolveTemplate(config.getSubject(), inputs);
        String finalContent = resolveContent(inputs);
        String resolvedTo = resolveTemplate(config.getToRecipients(), inputs);
        String resolvedCc = resolveTemplate(config.getCcRecipients(), inputs);

        // 发送邮件
        ChannelSender sender = new EmailSender();
        Map<String, Object> extras = new HashMap<>();
        extras.put("toRecipients", resolvedTo);
        extras.put("ccRecipients", resolvedCc);
        MessageParams params = new MessageParams(resolvedSubject, finalContent, extras);
        sender.send(channel, params);

        // 记录执行上下文
        output.addExecutionContext("channelName", channel.getName());
        output.addExecutionContext("channelType", channel.getType().name());
        output.addExecutionContext("toRecipients", resolvedTo);
        output.addExecutionContext("subject", resolvedSubject);

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, true);
        output.markComplete();
        return output;
    }

    private String resolveContent(Map<String, Object> inputs) {
        if (!FuncUtils.isEmpty(config.getContentTemplate())) {
            return resolveTemplate(config.getContentTemplate(), inputs);
        }
        return config.getContent();
    }

    private String resolveTemplate(String template, Map<String, Object> inputs) {
        if (template == null || inputs == null || inputs.isEmpty()) {
            return template;
        }
        Object resolved = formatter.format(template, inputs, false);
        return resolved != null ? resolved.toString() : template;
    }

    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (FuncUtils.isEmpty(config.getChannelId())) {
            return VerifyResult.invalid(new VerifyFail("channelId", "渠道ID不能为空"));
        }
        if (FuncUtils.isEmpty(config.getToRecipients())) {
            return VerifyResult.invalid(new VerifyFail("toRecipients", "收件人不能为空"));
        }
        if (FuncUtils.isEmpty(config.getContent()) && FuncUtils.isEmpty(config.getContentTemplate())) {
            return VerifyResult.invalid(new VerifyFail("content", "邮件内容和内容模板不能同时为空"));
        }
        return VerifyResult.valid();
    }
}

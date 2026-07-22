package com.hxh.apboa.node.channel.wecom;

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
import com.hxh.apboa.node.channel.sender.MessageParams;
import com.hxh.apboa.node.channel.sender.WecomSender;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 企业微信发送节点
 *
 * @author huxuehao
 */
public class WecomSendNode extends EnhancedNode {

    @Getter
    private final Config config;
    private final TemplateFormatter formatter;

    public WecomSendNode(String id, String name, Config config) {
        super(id, name, NodeType.WECOM_SEND);
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
        ChannelMapper mapper = SpringContextHolder.getBean(ChannelMapper.class);
        Channel channel = mapper.selectById(config.getChannelId());
        if (channel == null) {
            throw new RuntimeException("渠道配置不存在，ID: " + config.getChannelId());
        }
        if (!Boolean.TRUE.equals(channel.getEnabled())) {
            throw new RuntimeException("渠道未启用，ID: " + config.getChannelId());
        }

        String finalContent = resolveContent(inputs);
        String resolvedMentionUsers = resolveTemplate(config.getMentionUsers(), inputs);
        String resolvedMentionMobiles = resolveTemplate(config.getMentionMobiles(), inputs);

        ChannelSender sender = new WecomSender();
        Map<String, Object> extras = new HashMap<>();
        extras.put("mentionUsers", resolvedMentionUsers);
        extras.put("mentionMobiles", resolvedMentionMobiles);
        MessageParams params = new MessageParams(null, finalContent, extras);

        if (config.isSyncExecute()) {
            sender.send(channel, params);

            output.addExecutionContext("channelName", channel.getName());
            output.addExecutionContext("channelType", channel.getType().name());

            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, true);
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    sender.send(channel, params);
                } catch (Exception e) {
                    System.out.println(getName() + "异步执行失败: " + e.getMessage());
                }
            });
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, "ASYNC_EXECUTE");
        }

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
        if (FuncUtils.isEmpty(config.getContent()) && FuncUtils.isEmpty(config.getContentTemplate())) {
            return VerifyResult.invalid(new VerifyFail("content", "消息内容和内容模板不能同时为空"));
        }
        return VerifyResult.valid();
    }
}

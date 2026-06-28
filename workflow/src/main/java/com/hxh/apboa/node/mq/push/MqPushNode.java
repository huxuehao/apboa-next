package com.hxh.apboa.node.mq.push;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.mq.entity.Mq;
import com.hxh.apboa.mq.mapper.MqMapper;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.node.mq.push.sender.MQSender;
import com.hxh.apboa.node.mq.push.sender.MQSenderFactory;
import lombok.Getter;

import java.util.Map;

/**
 * 描述：消息推送节点
 * 根据配置的 MQ ID 获取连接，向指定的消息中间件推送消息。
 * 支持 Kafka、RabbitMQ、RocketMQ 三种消息中间件。
 *
 * @author huxuehao
 **/
public class MqPushNode extends EnhancedNode {

    @Getter
    private final Config config;
    private final TemplateFormatter formatter;

    public MqPushNode(String id, String name, Config config) {
        super(id, name, NodeType.MQ_PUSH);
        this.config = config;
        this.formatter = TemplateFormatterFactory.createFormatter(config.getTemplateType());
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
     * 创建成功输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) throws Exception {
        // TODO 这个地方不能每次都做MQ配置获取，浪费性能，一定要缓存，直接通过MQ ID获取MQ发送器
        // 获取 MQ 配置
        MqMapper mapper = SpringContextHolder.getBean(MqMapper.class);
        Mq mq = mapper.selectById(config.getMqId());
        if (mq == null) {
            throw new RuntimeException("MQ 配置不存在，MQ ID: " + config.getMqId());
        }
        if (!Boolean.TRUE.equals(mq.getEnabled())) {
            throw new RuntimeException("MQ 未启用，MQ ID: " + config.getMqId());
        }

        // 解析 topicOrQueue、key 中的动态变量
        String resolvedTopic = resolveTemplate(config.getTopicOrQueue(), inputs);
        String resolvedKey = resolveTemplate(config.getKey(), inputs);

        // 解析消息内容
        String finalMessage = resolveMessage(inputs);

        // 获取 MQ 发送器并推送消息
        MQSender sender = MQSenderFactory.getSender(mq);
        sender.send(resolvedTopic, resolvedKey, finalMessage);

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, true);
        output.markComplete();
        return output;
    }

    /**
     * 解析消息内容
     * 优先使用 messageTemplate 进行模板变量替换，否则使用 message。
     */
    private String resolveMessage(Map<String, Object> inputs) {
        if (!FuncUtils.isEmpty(config.getMessageTemplate())) {
            return resolveTemplate(config.getMessageTemplate(), inputs);
        }
        return config.getMessage();
    }

    /**
     * 解析模板字符串中的动态变量
     */
    private String resolveTemplate(String template, Map<String, Object> inputs) {
        if (template == null || inputs == null || inputs.isEmpty()) {
            return template;
        }
        Object resolved = formatter.format(template, inputs, false);
        return resolved != null ? resolved.toString() : template;
    }

    /**
     * 异常节点输出
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (FuncUtils.isEmpty(config.getMqId())) {
            return VerifyResult.invalid(new VerifyFail("mqId", "MQ ID 不能为空"));
        }
        if (FuncUtils.isEmpty(config.getTopicOrQueue())) {
            return VerifyResult.invalid(new VerifyFail("topicOrQueue", "主题或队列名称不能为空"));
        }
        if (FuncUtils.isEmpty(config.getMessage()) && FuncUtils.isEmpty(config.getMessageTemplate())) {
            return VerifyResult.invalid(new VerifyFail("message", "消息内容和消息模板不能同时为空"));
        }
        return VerifyResult.valid();
    }
}

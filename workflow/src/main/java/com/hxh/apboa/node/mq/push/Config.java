package com.hxh.apboa.node.mq.push;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：消息推送节点配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {

    /**
     * MQ ID，对应 apboa-mq 中配置的 MQ 实例
     */
    private String mqId;

    /**
     * 主题或队列名称
     */
    private String topicOrQueue;

    /**
     * 消息键（可选）
     * Kafka: 分区键
     * RabbitMQ: routing key
     * RocketMQ: tag
     */
    private String key;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息模板（可选，优先级高于 message）
     * 支持变量替换，格式如 "Hello, ${name}!"
     */
    private String messageTemplate;

    /**
     * 消息模板格式化器类型，默认使用 STRING（简单变量替换）
     */
    private FormatterType templateType = FormatterType.STRING;
}

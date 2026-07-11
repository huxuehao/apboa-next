package com.hxh.apboa.node.mq.push.sender;

/**
 * 描述：消息发送器接口
 * 定义统一的消息推送抽象，不同 MQ 中间件（Kafka、RabbitMQ、RocketMQ 等）
 * 分别实现此接口，通过策略模式支持多类型 MQ 的扩展。
 *
 * @author huxuehao
 **/
public interface MQSender {

    /**
     * 发送消息
     *
     * @param topicOrQueue 主题或队列名称
     * @param key          消息键（可选，Kafka 分区键或 RabbitMQ routing key）
     * @param message      消息内容
     * @throws Exception 发送失败时抛出
     */
    void send(String topicOrQueue, String key, String message) throws Exception;

    /**
     * 关闭发送器，释放连接资源
     */
    void close();
}

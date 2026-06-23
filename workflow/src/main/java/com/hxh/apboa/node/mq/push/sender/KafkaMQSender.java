package com.hxh.apboa.node.mq.push.sender;

import java.util.Properties;

/**
 * 描述：Kafka 消息发送器
 * 基于 Kafka 原生客户端实现消息推送。
 *
 * @author huxuehao
 **/
public class KafkaMQSender implements MQSender {

    private final String bootstrapServers;
    private final String username;
    private final String password;
    private Object producer;

    /**
     * 构造 Kafka 发送器
     *
     * @param address  Kafka broker 地址（如 localhost:9092）
     * @param port     端口（可冗余，address 中已包含）
     * @param username SASL 用户名
     * @param password SASL 密码
     * @param config   扩展配置（JSON 字符串，可包含额外的 Kafka 生产者参数）
     */
    public KafkaMQSender(String address, Integer port, String username, String password, String config) {
        this.bootstrapServers = address != null ? address : "localhost:9092";
        this.username = username;
        this.password = password;
        initProducer(config);
    }

    private void initProducer(String config) {
        try {
            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrapServers);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("acks", "1");
            props.put("retries", 3);

            // SASL 认证配置
            if (username != null && !username.isEmpty()) {
                props.put("security.protocol", "SASL_PLAINTEXT");
                props.put("sasl.mechanism", "PLAIN");
                props.put("sasl.jaas.config",
                        String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                                username, password));
            }

            // 使用反射创建 KafkaProducer，避免编译时强依赖
            Class<?> producerClass = Class.forName("org.apache.kafka.clients.producer.KafkaProducer");
            this.producer = producerClass.getConstructor(Properties.class).newInstance(props);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Kafka 客户端未引入，请在 pom.xml 中添加 kafka-clients 依赖", e);
        } catch (Exception e) {
            throw new RuntimeException("Kafka 生产者初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void send(String topicOrQueue, String key, String message) throws Exception {
        if (producer == null) {
            throw new RuntimeException("Kafka 生产者未初始化");
        }

        try {
            // 使用反射创建 ProducerRecord 并发送
            Class<?> recordClass = Class.forName("org.apache.kafka.clients.producer.ProducerRecord");
            Object record = recordClass.getConstructor(String.class, String.class, String.class)
                    .newInstance(topicOrQueue, key, message);

            producer.getClass().getMethod("send", recordClass).invoke(producer, record);
            producer.getClass().getMethod("flush").invoke(producer);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Kafka 客户端未引入", e);
        }
    }

    @Override
    public void close() {
        if (producer != null) {
            try {
                producer.getClass().getMethod("close").invoke(producer);
            } catch (Exception ignored) {
            }
        }
    }
}

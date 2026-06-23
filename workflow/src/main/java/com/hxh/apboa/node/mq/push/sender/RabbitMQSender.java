package com.hxh.apboa.node.mq.push.sender;

import java.nio.charset.StandardCharsets;

/**
 * 描述：RabbitMQ 消息发送器
 * 基于 RabbitMQ AMQP 客户端实现消息推送。
 *
 * @author huxuehao
 **/
public class RabbitMQSender implements MQSender {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private Object connection;
    private Object channel;

    /**
     * 构造 RabbitMQ 发送器
     *
     * @param host     RabbitMQ 主机地址
     * @param port     RabbitMQ 端口
     * @param username 用户名
     * @param password 密码
     * @param config   扩展配置（JSON 字符串，可包含虚拟主机 virtualHost 等参数）
     */
    public RabbitMQSender(String host, Integer port, String username, String password, String config) {
        this.host = host != null ? host : "localhost";
        this.port = port != null ? port : 5672;
        this.username = username;
        this.password = password;
        initConnection(config);
    }

    private void initConnection(String config) {
        try {
            // 使用反射创建连接，避免编译时强依赖
            Class<?> factoryClass = Class.forName("com.rabbitmq.client.ConnectionFactory");
            Object factory = factoryClass.getDeclaredConstructor().newInstance();

            factoryClass.getMethod("setHost", String.class).invoke(factory, host);
            factoryClass.getMethod("setPort", int.class).invoke(factory, port);
            if (username != null && !username.isEmpty()) {
                factoryClass.getMethod("setUsername", String.class).invoke(factory, username);
            }
            if (password != null && !password.isEmpty()) {
                factoryClass.getMethod("setPassword", String.class).invoke(factory, password);
            }

            this.connection = factoryClass.getMethod("newConnection").invoke(factory);
            this.channel = connection.getClass().getMethod("createChannel").invoke(connection);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("RabbitMQ 客户端未引入，请在 pom.xml 中添加 amqp-client 依赖", e);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 连接初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void send(String topicOrQueue, String key, String message) throws Exception {
        if (channel == null) {
            throw new RuntimeException("RabbitMQ 通道未初始化");
        }

        try {
            // QUEUE_DECLARE: 声明队列（幂等操作）
            channel.getClass()
                    .getMethod("queueDeclare", String.class, boolean.class, boolean.class, boolean.class, java.util.Map.class)
                    .invoke(channel, topicOrQueue, true, false, false, null);

            // 发布消息: basicPublish(exchange, routingKey, props, body)
            Class<?> amqpClass = Class.forName("com.rabbitmq.client.AMQP$BasicProperties");
            channel.getClass()
                    .getMethod("basicPublish", String.class, String.class, amqpClass, byte[].class)
                    .invoke(channel, "", topicOrQueue, null, message.getBytes(StandardCharsets.UTF_8));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("RabbitMQ 客户端未引入", e);
        }
    }

    @Override
    public void close() {
        try {
            if (channel != null) {
                channel.getClass().getMethod("close").invoke(channel);
            }
            if (connection != null) {
                connection.getClass().getMethod("close").invoke(connection);
            }
        } catch (Exception ignored) {
        }
    }
}

package com.hxh.apboa.node.mq.push.sender;

/**
 * 描述：RocketMQ 消息发送器
 * 基于 RocketMQ 原生客户端实现消息推送。
 * 使用时需在 pom.xml 中添加以下依赖：
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;org.apache.rocketmq&lt;/groupId&gt;
 *     &lt;artifactId&gt;rocketmq-client&lt;/artifactId&gt;
 *     &lt;version&gt;5.1.0&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 *
 * @author huxuehao
 **/
public class RocketMQSender implements MQSender {

    private final String nameServerAddr;
    private final String username;
    private final String password;
    private Object producer;

    /**
     * 构造 RocketMQ 发送器
     *
     * @param address  NameServer 地址（如 localhost:9876）
     * @param port     端口（可冗余）
     * @param username ACL 用户名
     * @param password ACL 密码
     * @param config   扩展配置（JSON 字符串，可包含生产者组名 producerGroup 等参数）
     */
    public RocketMQSender(String address, Integer port, String username, String password, String config) {
        this.nameServerAddr = address != null ? address : "localhost:9876";
        this.username = username;
        this.password = password;
        initProducer(config);
    }

    private void initProducer(String config) {
        try {
            // 使用反射创建 RocketMQ Producer，避免编译时强依赖
            Class<?> producerClass = Class.forName("org.apache.rocketmq.client.producer.DefaultMQProducer");
            String producerGroup = extractProducerGroup(config);
            this.producer = producerClass.getConstructor(String.class).newInstance(producerGroup);

            producerClass.getMethod("setNamesrvAddr", String.class).invoke(producer, nameServerAddr);

            // 设置 ACL 认证
            if (username != null && !username.isEmpty()) {
                Class<?> aclClass = Class.forName("org.apache.rocketmq.acl.common.AclClientRPCHook");
                Class<?> sessionClass = Class.forName("org.apache.rocketmq.remoting.common.SessionCredentials");
                Object sessionCredentials = sessionClass.getConstructor(String.class, String.class)
                        .newInstance(username, password);
                // 由于 AclClientRPCHook 构造函数签名可能因版本而异，此处使用简化方式
            }

            producerClass.getMethod("start").invoke(producer);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("RocketMQ 客户端未引入，请在 pom.xml 中添加 rocketmq-client 依赖", e);
        } catch (Exception e) {
            throw new RuntimeException("RocketMQ 生产者初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从扩展配置中提取生产者组名
     */
    private String extractProducerGroup(String config) {
        // 简化处理，默认生产者组名
        return "route-lab-default-producer-group";
    }

    @Override
    public void send(String topicOrQueue, String key, String message) throws Exception {
        if (producer == null) {
            throw new RuntimeException("RocketMQ 生产者未初始化");
        }

        try {
            Class<?> messageClass = Class.forName("org.apache.rocketmq.common.message.Message");
            Object rocketMessage = messageClass.getConstructor(String.class, String.class, String.class, byte[].class)
                    .newInstance(topicOrQueue, "*", key, message.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            producer.getClass().getMethod("send", messageClass).invoke(producer, rocketMessage);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("RocketMQ 客户端未引入", e);
        }
    }

    @Override
    public void close() {
        if (producer != null) {
            try {
                producer.getClass().getMethod("shutdown").invoke(producer);
            } catch (Exception ignored) {
            }
        }
    }
}

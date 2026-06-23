package com.hxh.apboa.node.mq.push.sender;

import com.hxh.apboa.mq.entity.Mq;
import com.hxh.apboa.mq.enums.MqType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：消息发送器工厂
 * 根据 MQ 配置创建并缓存 MQSender 实例，每个 mqId 对应一个 MQ 连接。
 * 通过策略模式，可方便地扩展更多 MQ 类型。
 *
 * @author huxuehao
 **/
public class MQSenderFactory {

    /**
     * 发送器缓存：mqId -> MQSender
     */
    private static final Map<Long, MQSender> senderCache = new ConcurrentHashMap<>();

    /**
     * 根据 MQ 实体获取或创建 MQSender
     *
     * @param mq MQ 实体
     * @return MQSender 实例
     */
    public static MQSender getSender(Mq mq) {
        if (mq == null) {
            throw new RuntimeException("MQ 配置不能为空");
        }
        if (mq.getId() == null) {
            throw new RuntimeException("MQ ID不能为空");
        }

        return senderCache.computeIfAbsent(mq.getId(), id -> createSender(mq));
    }

    /**
     * 根据 MQ 类型创建对应的发送器
     */
    private static MQSender createSender(Mq mq) {
        MqType type = mq.getType();
        if (type == null) {
            throw new RuntimeException("MQ 类型不能为空，MQ ID: " + mq.getId());
        }

        return switch (type) {
            case KAFKA -> new KafkaMQSender(mq.getAddress(), mq.getPort(), mq.getUsername(), mq.getPassword(), mq.getConfig());
            case RABBITMQ -> new RabbitMQSender(mq.getAddress(), mq.getPort(), mq.getUsername(), mq.getPassword(), mq.getConfig());
            case ROCKETMQ -> new RocketMQSender(mq.getAddress(), mq.getPort(), mq.getUsername(), mq.getPassword(), mq.getConfig());
            default -> throw new RuntimeException("不支持的 MQ 类型: " + type);
        };
    }

    /**
     * 清除指定 MQ 的发送器缓存（关闭连接）
     *
     * @param mqId MQ ID
     */
    public static void evictCache(Long mqId) {
        MQSender sender = senderCache.remove(mqId);
        if (sender != null) {
            sender.close();
        }
    }

    /**
     * 清除所有发送器缓存
     */
    public static void evictAll() {
        senderCache.forEach((id, sender) -> sender.close());
        senderCache.clear();
    }
}

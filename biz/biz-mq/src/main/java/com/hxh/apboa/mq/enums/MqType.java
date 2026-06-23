package com.hxh.apboa.mq.enums;

/**
 * 描述：消息队列类型
 *
 * @author huxuehao
 **/
public enum MqType {
    KAFKA("kafka"),
    RABBITMQ("rabbitmq"),
    ROCKETMQ("rocketmq");

    MqType(String desc) {}
}

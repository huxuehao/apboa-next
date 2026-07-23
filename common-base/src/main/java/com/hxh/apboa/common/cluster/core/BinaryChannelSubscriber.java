package com.hxh.apboa.common.cluster.core;

import org.springframework.data.redis.listener.Topic;

/**
 * 描述：Redis频道二进制订阅者接口
 * 与 {@link ChannelSubscriber} 同构，但消息体以原始字节交付，
 * 用于音频流等二进制载荷（走字符串通道会被字符集转换破坏）。
 * 实现此接口并注册为Spring Bean，即可自动订阅Redis频道。
 *
 * @author huxuehao
 **/
public interface BinaryChannelSubscriber {

    /**
     * 获取订阅的频道主题
     *
     * @return 频道主题（支持PatternTopic或ChannelTopic）
     */
    Topic getTopic();

    /**
     * 处理接收到的二进制消息
     *
     * @param channel 频道名称
     * @param body    消息体原始字节
     */
    void onMessage(String channel, byte[] body);
}

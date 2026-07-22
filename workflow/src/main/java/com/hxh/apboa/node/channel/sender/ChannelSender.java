package com.hxh.apboa.node.channel.sender;

import com.hxh.apboa.channel.entity.Channel;

/**
 * 渠道消息发送器接口
 * 定义统一的消息发送抽象，不同渠道（邮件、企业微信、钉钉、飞书）
 * 分别实现此接口，通过策略模式支持多渠道扩展。
 *
 * @author huxuehao
 */
public interface ChannelSender {

    /**
     * 发送消息
     *
     * @param channel  渠道配置实体
     * @param params   消息参数（不同渠道参数结构不同）
     * @throws Exception 发送失败时抛出
     */
    void send(Channel channel, MessageParams params) throws Exception;
}

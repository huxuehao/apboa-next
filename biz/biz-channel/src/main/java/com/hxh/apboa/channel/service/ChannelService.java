package com.hxh.apboa.channel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.channel.entity.Channel;

import java.util.List;

/**
 * 通知渠道服务接口
 *
 * @author huxuehao
 */
public interface ChannelService extends IService<Channel> {
    /**
     * 删除渠道（支持强制删除）
     *
     * @param force       是否强制删除（1=强制，0=检查引用）
     * @param channelIds  渠道ID列表
     * @return 是否删除成功
     */
    boolean deleteChannel(Integer force, List<String> channelIds);

    /**
     * 更新渠道（保留空密码）
     *
     * @param channel 渠道实体
     * @return 是否更新成功
     */
    boolean updateChannel(Channel channel);

    /**
     * 更新启用状态
     *
     * @param channelId 渠道ID
     * @param enable    启用状态
     * @return 是否更新成功
     */
    boolean updateEnable(String channelId, Integer enable);

    /**
     * 测试渠道连接
     *
     * @param channel 渠道实体
     * @return 是否连接成功
     */
    boolean checkConnect(Channel channel);

    /**
     * 测试已保存渠道的连接
     *
     * @param channelId 渠道ID
     * @return 是否连接成功
     */
    boolean checkSavedConnect(String channelId);

    /**
     * 按启用状态列出渠道
     *
     * @param enabled 启用状态，null 表示全部
     * @return 渠道列表
     */
    List<Channel> listByEnabled(Integer enabled);
}

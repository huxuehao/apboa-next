package com.hxh.apboa.mq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.mq.entity.Mq;

import java.util.List;

/**
 * 描述：消息队列服务
 *
 * @author huxuehao
 **/
public interface MqService extends IService<Mq> {
    /**
     * 删除消息队列
     *
     * @param mqIds 消息队列ID
     * @return 是否删除成功
     */
    boolean deleteMq(Integer force, List<String> mqIds);

    /**
     * 更新消息队列启用状态
     *
     * @param mqId   消息队列ID
     * @param enable 状态值
     * @return 是否更新成功
     */
    boolean updateEnable(String mqId, Integer enable);
}

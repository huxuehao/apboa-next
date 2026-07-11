package com.hxh.apboa.mq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.mq.entity.Mq;

import java.util.List;

public interface MqService extends IService<Mq> {
    boolean deleteMq(Integer force, List<String> mqIds);

    boolean updateMq(Mq mq);

    boolean updateEnable(String mqId, Integer enable);

    boolean checkConnect(Mq mq);

    boolean checkSavedConnect(String mqId);

    List<Mq> listByEnabled(Integer enabled);
}

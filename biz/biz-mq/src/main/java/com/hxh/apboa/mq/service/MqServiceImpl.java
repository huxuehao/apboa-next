package com.hxh.apboa.mq.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.mq.entity.Mq;
import com.hxh.apboa.mq.mapper.MqMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 描述：消息队列服务实现
 *
 * @author huxuehao
 **/
@Service
public class MqServiceImpl extends ServiceImpl<MqMapper, Mq> implements MqService {
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMq(Integer force, List<String> mqIds) {
        if (force == 0) {
            throw new RuntimeException("请选择强制删除");
        }
        return this.removeByIds(mqIds);
    }

    @Override
    public boolean updateEnable(String mqId, Integer enable) {
        return lambdaUpdate()
                .set(Mq::getEnabled, enable)
                .eq(Mq::getId, mqId)
                .update();
    }
}

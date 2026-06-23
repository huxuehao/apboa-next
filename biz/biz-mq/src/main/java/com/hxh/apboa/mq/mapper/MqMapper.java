package com.hxh.apboa.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.mq.entity.Mq;
import org.apache.ibatis.annotations.Mapper;

/**
 * 描述：消息队列表ROM
 *
 * @author huxuehao
 **/
@Mapper
public interface MqMapper extends BaseMapper<Mq> {
}

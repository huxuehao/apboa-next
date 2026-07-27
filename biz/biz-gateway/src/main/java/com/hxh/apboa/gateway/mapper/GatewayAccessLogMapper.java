package com.hxh.apboa.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 描述：网关访问日志Mapper
 * 表已加入租户拦截忽略清单，查询时需显式过滤租户
 *
 * @author huxuehao
 **/
@Mapper
public interface GatewayAccessLogMapper extends BaseMapper<GatewayAccessLog> {
}

package com.hxh.apboa.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.DashboardHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 描述：Dashboard 个人历史版本 Mapper
 *
 * @author huxuehao
 **/
@Mapper
public interface DashboardHistoryMapper extends BaseMapper<DashboardHistory> {
}

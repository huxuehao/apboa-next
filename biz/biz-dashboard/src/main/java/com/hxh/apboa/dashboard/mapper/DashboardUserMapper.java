package com.hxh.apboa.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.common.entity.DashboardUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 描述：Dashboard 个人覆盖 Mapper
 *
 * @author huxuehao
 **/
@Mapper
public interface DashboardUserMapper extends BaseMapper<DashboardUser> {
}

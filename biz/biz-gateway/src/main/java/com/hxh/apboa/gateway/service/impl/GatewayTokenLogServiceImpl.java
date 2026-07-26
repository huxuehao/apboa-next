package com.hxh.apboa.gateway.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.gateway.entity.GatewayTokenLog;
import com.hxh.apboa.gateway.mapper.GatewayTokenLogMapper;
import com.hxh.apboa.gateway.service.GatewayTokenLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 描述：网关Token颁发日志服务实现
 * 日志表在租户拦截忽略清单中，所有查询显式过滤当前租户
 *
 * @author huxuehao
 **/
@Service
public class GatewayTokenLogServiceImpl extends ServiceImpl<GatewayTokenLogMapper, GatewayTokenLog> implements GatewayTokenLogService {

    @Override
    public IPage<GatewayTokenLog> pageLogs(GatewayTokenLog query, PageParams pageParams) {
        QueryWrapper<GatewayTokenLog> qw = MP.getQueryWrapper(query);
        qw.eq("tenant_id", TenantUtils.getCurrentTenantId());
        qw.orderByDesc("id");
        return page(MP.getPage(pageParams), qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteLogs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        QueryWrapper<GatewayTokenLog> qw = new QueryWrapper<>();
        qw.eq("tenant_id", TenantUtils.getCurrentTenantId()).in("id", ids);
        return remove(qw);
    }
}

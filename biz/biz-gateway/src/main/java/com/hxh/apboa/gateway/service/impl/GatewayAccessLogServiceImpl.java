package com.hxh.apboa.gateway.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import com.hxh.apboa.gateway.mapper.GatewayAccessLogMapper;
import com.hxh.apboa.gateway.service.GatewayAccessLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 描述：网关访问日志服务实现
 * 日志表在租户拦截忽略清单中，所有查询显式过滤当前租户
 *
 * @author huxuehao
 **/
@Service
public class GatewayAccessLogServiceImpl extends ServiceImpl<GatewayAccessLogMapper, GatewayAccessLog> implements GatewayAccessLogService {

    @Override
    public IPage<GatewayAccessLog> pageLogs(GatewayAccessLog query, PageParams pageParams) {
        QueryWrapper<GatewayAccessLog> qw = MP.getQueryWrapper(query);
        qw.eq("tenant_id", TenantUtils.getCurrentTenantId());
        // 列表仅返回摘要列，正文大字段在详情中查看
        qw.select("id", "tenant_id", "app_id", "api_id", "workflow_run_id", "method", "path",
                "access_ip", "status", "http_status", "start_time", "end_time", "created_at");
        qw.orderByDesc("id");
        return page(MP.getPage(pageParams), qw);
    }

    @Override
    public GatewayAccessLog logDetail(Long id) {
        GatewayAccessLog log = getById(id);
        if (log == null || !TenantUtils.getCurrentTenantId().equals(log.getTenantId())) {
            throw new RuntimeException("访问日志不存在");
        }
        return log;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteLogs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        QueryWrapper<GatewayAccessLog> qw = new QueryWrapper<>();
        qw.eq("tenant_id", TenantUtils.getCurrentTenantId()).in("id", ids);
        return remove(qw);
    }
}

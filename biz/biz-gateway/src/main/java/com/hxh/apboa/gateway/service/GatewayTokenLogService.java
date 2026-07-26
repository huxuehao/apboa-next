package com.hxh.apboa.gateway.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.gateway.entity.GatewayTokenLog;

import java.util.List;

/**
 * 描述：网关Token颁发日志服务
 *
 * @author huxuehao
 **/
public interface GatewayTokenLogService extends IService<GatewayTokenLog> {

    /**
     * 分页查询（显式过滤当前租户）
     */
    IPage<GatewayTokenLog> pageLogs(GatewayTokenLog query, PageParams pageParams);

    /**
     * 批量删除日志
     */
    boolean deleteLogs(List<Long> ids);
}

package com.hxh.apboa.gateway.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.gateway.entity.GatewayAccessLog;

import java.util.List;

/**
 * 描述：网关访问日志服务
 *
 * @author huxuehao
 **/
public interface GatewayAccessLogService extends IService<GatewayAccessLog> {

    /**
     * 分页查询（摘要列，显式过滤当前租户）
     */
    IPage<GatewayAccessLog> pageLogs(GatewayAccessLog query, PageParams pageParams);

    /**
     * 日志详情（校验租户归属）
     */
    GatewayAccessLog logDetail(Long id);

    /**
     * 批量删除日志
     */
    boolean deleteLogs(List<Long> ids);
}

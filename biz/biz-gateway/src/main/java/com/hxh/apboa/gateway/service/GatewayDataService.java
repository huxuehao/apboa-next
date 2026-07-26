package com.hxh.apboa.gateway.service;

import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.option.GatewayAppOption;

import java.util.List;

/**
 * 描述：网关数据面装配服务
 * 面向网关运行时节点，跨租户加载应用与API的运行时选项
 *
 * @author huxuehao
 **/
public interface GatewayDataService {

    /**
     * 加载所有在线应用
     */
    List<GatewayAppOption> loadOnlineApps();

    /**
     * 按ID加载应用
     */
    List<GatewayAppOption> loadApps(List<Long> ids);

    /**
     * 加载指定应用下所有在线API
     */
    List<GatewayApiOption> loadOnlineApis(Long appId);

    /**
     * 按ID加载API
     */
    List<GatewayApiOption> loadApis(List<Long> ids);

    /**
     * 查询绑定了指定工作流且当前在线的API ID集合
     */
    List<Long> onlineApiIdsBoundToWorkflow(Long workflowId);
}

package com.hxh.apboa.gateway.vo;

import com.hxh.apboa.gateway.entity.GatewayApi;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关API视图对象（附带应用与工作流关联信息）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class GatewayApiVO extends GatewayApi {
    /**
     * 所属应用名称
     */
    private String appName;
    /**
     * 所属应用端口
     */
    private Integer appPort;
    /**
     * 绑定的工作流ID
     */
    private Long workflowId;
    /**
     * 绑定的工作流名称
     */
    private String workflowName;
    /**
     * 绑定的工作流状态
     */
    private String workflowStatus;
}

package com.hxh.apboa.gateway.option;

import com.hxh.apboa.gateway.enums.GatewayHttpMethod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 描述：网关API运行时选项（数据面挂载路由所需的完整信息）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@NoArgsConstructor
public class GatewayApiOption {
    private Long id;
    private Long appId;
    private Long tenantId;
    private String name;
    private GatewayHttpMethod method;
    private String path;
    /**
     * 绑定的工作流ID（执行时取最新已发布版本）
     */
    private Long workflowId;
    /**
     * API创建人（作为工作流执行者）
     */
    private Long createdBy;
    private GatewayApiConfig config;

    public GatewayApiOption(Long id, Long appId, Long tenantId, String name, GatewayHttpMethod method,
                            String path, Long workflowId, Long createdBy, GatewayApiConfig config) {
        this.id = id;
        this.appId = appId;
        this.tenantId = tenantId;
        this.name = name;
        this.method = method;
        this.path = path;
        this.workflowId = workflowId;
        this.createdBy = createdBy;
        this.config = config == null ? new GatewayApiConfig() : config;
    }
}

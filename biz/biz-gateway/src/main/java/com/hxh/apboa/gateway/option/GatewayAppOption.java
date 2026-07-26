package com.hxh.apboa.gateway.option;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 描述：网关应用运行时选项（数据面部署应用所需的完整信息）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@NoArgsConstructor
public class GatewayAppOption {
    private Long id;
    private Long tenantId;
    private String name;
    private Integer port;
    private GatewayAppConfig config;

    public GatewayAppOption(Long id, Long tenantId, String name, Integer port, GatewayAppConfig config) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.port = port;
        this.config = config == null ? new GatewayAppConfig() : config;
    }
}

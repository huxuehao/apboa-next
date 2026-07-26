package com.hxh.apboa.gateway.vo;

import com.hxh.apboa.gateway.entity.GatewayClient;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：网关客户端视图对象（附带授权的API集合）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class GatewayClientVO extends GatewayClient {
    /**
     * 已授权的API ID集合
     */
    private List<Long> apiIds;
    /**
     * 已授权的API数量
     */
    private Integer apiCount;
}

package com.hxh.apboa.gateway.cluster;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 描述：网关集群同步消息（经Redis发布订阅广播到所有网关节点）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@NoArgsConstructor
public class GatewaySyncMessage {
    /**
     * 消息类型
     */
    private GatewaySyncType type;
    /**
     * 关联的实体ID集合（应用/API）
     */
    private List<Long> ids;

    public GatewaySyncMessage(GatewaySyncType type, List<Long> ids) {
        this.type = type;
        this.ids = ids;
    }
}

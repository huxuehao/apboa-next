package com.hxh.apboa.gateway.cluster;

/**
 * 描述：网关集群同步消息类型
 *
 * @author huxuehao
 **/
public enum GatewaySyncType {
    /** 应用上线 */
    APP_ONLINE,
    /** 应用下线 */
    APP_OFFLINE,
    /** 应用配置变更（下线后重新上线） */
    APP_RESET,
    /** API上线 */
    API_ONLINE,
    /** API下线 */
    API_OFFLINE,
    /** API配置变更（先卸载后重挂载） */
    API_RESET
}

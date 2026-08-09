package com.hxh.apboa.heartbeat;

/**
 * 描述：心跳上报请求体
 * 由 runner 服务定期发送到 console 的 /api/heartbeat/report 接口
 *
 * @author huxuehao
 **/
public class HeartbeatPayload {

    /** 节点唯一标识 */
    private String nodeId;

    /** 服务类型：FILE / PROXY / RUNTIME / GATEWAY */
    private String serviceType;

    /** 主机名 */
    private String hostname;

    /** IP 地址 */
    private String ip;

    /** 服务端口（runner-file 无 Web 服务器时为 null） */
    private Integer port;

    /** 心跳时间戳（毫秒） */
    private long timestamp;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

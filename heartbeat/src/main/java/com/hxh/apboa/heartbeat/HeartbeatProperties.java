package com.hxh.apboa.heartbeat;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 描述：心跳上报配置属性
 * 支持 YAML 配置和环境变量两种方式，优先级：环境变量 APBOA_NODE_ID > YAML apboa.heartbeat.node-id > hostname
 *
 * @author huxuehao
 **/
@ConfigurationProperties(prefix = "heartbeat")
public class HeartbeatProperties {

    /** 是否启用心跳上报 */
    private boolean enabled = false;

    /** console 服务基础 URL */
    private String consoleUrl = "http://localhost:3060";

    /** 节点唯一标识（默认自动检测 hostname） */
    private String nodeId;

    /** 服务类型：FILE / PROXY / RUNTIME / GATEWAY */
    private String serviceType;

    /** 心跳发送间隔（秒） */
    private int intervalSeconds = 30;

    /** 当前服务端口（runner-file 无 Web 服务器时为 null） */
    private Integer port;

    @PostConstruct
    public void init() {
        // 优先读环境变量 APBOA_NODE_ID
        String envNodeId = System.getenv("APBOA_NODE_ID");
        if (envNodeId != null && !envNodeId.isBlank()) {
            this.nodeId = envNodeId;
        } else if (this.nodeId == null || this.nodeId.isBlank()) {
            // 回退到 hostname
            try {
                this.nodeId = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                this.nodeId = "unknown-" + System.currentTimeMillis();
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConsoleUrl() {
        return consoleUrl;
    }

    public void setConsoleUrl(String consoleUrl) {
        this.consoleUrl = consoleUrl;
    }

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

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}

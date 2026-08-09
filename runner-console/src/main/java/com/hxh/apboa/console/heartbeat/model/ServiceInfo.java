package com.hxh.apboa.console.heartbeat.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：单个服务的状态信息
 * 用于向前端返回节点内各服务（FILE/PROXY/RUNTIME）的运行状态
 *
 * @author huxuehao
 **/
@Setter
@Getter
public class ServiceInfo {

    /** 服务类型：FILE / PROXY / RUNTIME / GATEWAY */
    private String serviceType;

    /** 服务状态：UP / DOWN */
    private String status;

    /** 服务端口（runner-file 无 Web 服务器时为 null） */
    private Integer port;

    /** 最近一次心跳时间 */
    private String lastHeartbeat;

    /** 当前进程首次心跳时间（用于计算连续运行时长） */
    private String startedAt;

}

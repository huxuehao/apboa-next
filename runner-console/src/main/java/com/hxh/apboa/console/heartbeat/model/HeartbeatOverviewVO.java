package com.hxh.apboa.console.heartbeat.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：节点监控总览 VO（执行节点 + WebSocket 节点合一）
 *
 * @author huxuehao
 **/
@Setter
@Getter
public class HeartbeatOverviewVO {

    /** 执行节点状态列表 */
    private List<NodeStatusVO> nodes;

    /** WebSocket 消息服务节点状态列表 */
    private List<WebSocketNodeVO> websocketNodes;
}

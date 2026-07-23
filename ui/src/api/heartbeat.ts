import request from '@/utils/request'
import type { ApiResponse, HeartbeatOverviewVO, NodeStatusVO, WebSocketNodeVO } from '@/types'

/**
 * 查询所有执行节点状态
 * GET /heartbeat/nodes
 */
export function listNodes() {
  return request.get<ApiResponse<NodeStatusVO[]>>('/api/heartbeat/nodes')
}

/**
 * 查询所有 WebSocket 消息服务节点状态
 * GET /heartbeat/websocket
 */
export function listWebSocketNodes() {
  return request.get<ApiResponse<WebSocketNodeVO[]>>('/api/heartbeat/websocket')
}

/**
 * 节点监控总览（执行节点 + WebSocket 节点合一）
 * GET /heartbeat/overview
 */
export function overview() {
  return request.get<ApiResponse<HeartbeatOverviewVO>>('/api/heartbeat/overview')
}

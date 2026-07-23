/**
 * AGUI SSE 模块：工厂与统一导出
 */

import { getAgentRunURL, getSSEHeaders, getRESTHeaders, getReconnectURL, getResumeURL, getPendingURL, getStatusURL, getStatusBatchURL, getStopURL, getActiveRunsURL, getSubagentResumeURL, getSubagentPendingURL } from './request'
import { AgentClient } from './agent-client'
import type { EventHandlers, ToolHandler } from './agent-client'
import type { RunAgentInput } from '@/types'

export type { EventHandlers, ToolHandler, EventMiddleware } from './agent-client'
export { AgentClient } from './agent-client'

export interface CreateAgentClientOptions {
  /** 覆盖默认 run URL */
  url?: string
  /** 覆盖默认请求头（会与 SSE 基础头合并） */
  headers?: Record<string, string>
  /** 事件回调 */
  handlers?: EventHandlers
  /** 工具名 -> 执行函数 */
  toolHandlers?: Record<string, ToolHandler>
}

/**
 * 创建已配置 baseURL 与认证头的 AgentClient
 * @param options 可选 URL/headers/handlers/toolHandlers 覆盖
 * @returns AgentClient 实例
 */
export function createAgentClient(
  options: CreateAgentClientOptions = {}
): AgentClient {
  const url = options.url ?? getAgentRunURL()
  const headers = { ...getSSEHeaders(), ...options.headers }
  return new AgentClient(
    url,
    headers,
    options.handlers ?? {},
    options.toolHandlers ?? {}
  )
}

export type { RunAgentInput }

export { getReconnectURL, getResumeURL, getPendingURL, getStatusURL, getStopURL, getActiveRunsURL }

/**
 * 查询指定会话的运行状态
 * @param threadId 会话 ID
 * @returns { running: boolean }
 */
export async function getStatus(threadId: string): Promise<boolean> {
  const url = getStatusURL(threadId)
  const headers = getRESTHeaders()
  const resp = await fetch(url, { headers })
  if (!resp.ok) throw new Error(`Status check failed: ${resp.status}`)
  const data = await resp.json()
  return data.running === true
}

/**
 * 批量查询多个会话的运行状态（一次请求替代逐个轮询）
 * @param threadIds 会话 ID 列表
 * @returns threadId -> running 的映射
 */
export async function getStatusBatch(threadIds: string[]): Promise<Record<string, boolean>> {
  if (threadIds.length === 0) return {}
  const url = getStatusBatchURL(threadIds)
  const headers = getRESTHeaders()
  const resp = await fetch(url, { headers })
  if (!resp.ok) throw new Error(`Batch status check failed: ${resp.status}`)
  return await resp.json()
}

/**
 * 强制停止指定会话的智能体
 * @param threadId 会话 ID
 */
export async function stopRun(threadId: string): Promise<void> {
  const url = getStopURL(threadId)
  const headers = getRESTHeaders()
  const resp = await fetch(url, { method: 'POST', headers })
  if (!resp.ok) throw new Error(`Stop failed: ${resp.status}`)
}

/**
 * 获取所有活跃运行的线程 ID 列表
 * @returns threadId 数组
 */
export async function getActiveRuns(): Promise<string[]> {
  const url = getActiveRunsURL()
  const headers = getRESTHeaders()
  const resp = await fetch(url, { headers })
  if (!resp.ok) throw new Error(`Active runs check failed: ${resp.status}`)
  return await resp.json()
}

/**
 * HITL 刷新恢复：获取会话的待确认工具列表（从后端持久暂停态重建）
 * @param threadId 会话 ID
 * @returns 待确认工具 [{toolUseId,name,input}]；无暂停态返回空数组
 */
export async function getPending(
  threadId: string
): Promise<Array<{ toolUseId: string; name: string; input?: Record<string, unknown> }>> {
  const url = getPendingURL(threadId)
  const headers = getRESTHeaders()
  const resp = await fetch(url, { headers })
  if (!resp.ok) throw new Error(`Pending check failed: ${resp.status}`)
  const data = await resp.json()
  return data.pending ?? []
}

/** 子智能体挂起中确认请求（SUBAGENT_CONFIRM_REQUIRED 事件载荷 / subagent/pending 返回元素同构） */
export interface SubPendingInfo {
  subSessionId: string
  parentThreadId?: string
  parentToolCallId: string
  subagentName: string
  pending: Array<{ toolUseId: string; name: string; input?: Record<string, unknown> }>
}

/**
 * 子智能体 HITL：提交确认决策，唤醒挂起等待的子智能体续跑（续跑事件沿原 SSE 流下发）
 * @returns resumed=false 表示挂起已失效（超时按全拒绝处理，或已决策）
 */
export async function subagentResume(
  subSessionId: string,
  decisions: Array<{ toolUseId: string; name: string; approved: boolean }>
): Promise<{ resumed: boolean; error?: string }> {
  const url = getSubagentResumeURL()
  const headers = getRESTHeaders()
  const resp = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ subSessionId, decisions })
  })
  if (!resp.ok) throw new Error(`Subagent resume failed: ${resp.status}`)
  return await resp.json()
}

/**
 * 子智能体 HITL 刷新恢复：查询主会话下所有挂起中的子确认请求
 * @param threadId 主会话 ID
 */
export async function getSubagentPending(threadId: string): Promise<SubPendingInfo[]> {
  const url = getSubagentPendingURL(threadId)
  const headers = getRESTHeaders()
  const resp = await fetch(url, { headers })
  if (!resp.ok) throw new Error(`Subagent pending check failed: ${resp.status}`)
  const data = await resp.json()
  return data.pending ?? []
}

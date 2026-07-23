/**
 * AGUI SSE 请求：基础 URL 与认证头
 * 独立于 axios 实例，供 fetch + ReadableStream 使用
 */

import { getToken } from '@/utils/auth'
import setting from '@/config/setting'

/** 默认 run 端点路径 */
const DEFAULT_RUN_PATH = '/api/runtime/agui/run'

/**
 * 获取智能体 run 的完整 URL
 * 开发环境直接请求后端，避免 Vite 代理缓冲 SSE 流
 */
export function getAgentRunURL(): string {
  const base = (import.meta.env.VITE_APP_BASE_API as string) || ''
  const path = base.endsWith('/') ? DEFAULT_RUN_PATH.slice(1) : DEFAULT_RUN_PATH
  if (base) return `${base}${path}`
  return path
}

/**
 * 获取 SSE 请求所需头（Content-Type、Accept、Authorization）
 * @returns 请求头对象
 */
export function getSSEHeaders(): Record<string, string> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream'
  }
  if (token) {
    headers[setting.tokenHeader] = `Bearer ${token}`
  }
  return headers
}

/**
 * 获取 REST 请求所需头（Content-Type、Accept、Authorization）
 * 用于 stop/status/active-runs 等 JSON 端点
 * @returns 请求头对象
 */
export function getRESTHeaders(): Record<string, string> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  }
  if (token) {
    headers[setting.tokenHeader] = `Bearer ${token}`
  }
  return headers
}

/** 默认 SSE 端点基础路径 */
const DEFAULT_SSE_BASE = '/api/runtime/agui'

/**
 * 获取 SSE 断点重连 URL
 * @param threadId 会话 ID
 */
export function getReconnectURL(threadId: string): string {
  const base = (import.meta.env.VITE_APP_BASE_API as string) || ''
  const path = `${DEFAULT_SSE_BASE}/reconnect/${encodeURIComponent(threadId)}`
  if (base) return `${base}${path}`
  return path
}

/**
 * 获取运行状态查询 URL
 * @param threadId 会话 ID
 */
export function getStatusURL(threadId: string): string {
  const base = (import.meta.env.VITE_APP_BASE_API as string) || ''
  const path = `${DEFAULT_SSE_BASE}/status/${encodeURIComponent(threadId)}`
  if (base) return `${base}${path}`
  return path
}

/**
 * 获取强制停止 URL
 * @param threadId 会话 ID
 */
export function getStopURL(threadId: string): string {
  const base = (import.meta.env.VITE_APP_BASE_API as string) || ''
  const path = `${DEFAULT_SSE_BASE}/stop/${encodeURIComponent(threadId)}`
  if (base) return `${base}${path}`
  return path
}

/**
 * 获取活跃运行列表 URL
 */
export function getActiveRunsURL(): string {
  const base = (import.meta.env.VITE_APP_BASE_API as string) || ''
  const path = `${DEFAULT_SSE_BASE}/active-runs`
  if (base) return `${base}${path}`
  return path
}

/**
 * 获取 HITL resume URL（提交逐工具确认决策，续接 SSE 事件流）
 * @param threadId 会话 ID
 */
export function getResumeURL(threadId: string): string {
  const base = (import.meta.env.VITE_APP_BASE_API as string) || ''
  const path = `${DEFAULT_SSE_BASE}/resume/${encodeURIComponent(threadId)}`
  if (base) return `${base}${path}`
  return path
}

/**
 * 获取 HITL 待确认列表 URL（刷新/重进会话时从持久暂停态重建确认 UI）
 * @param threadId 会话 ID
 */
export function getPendingURL(threadId: string): string {
  const base = (import.meta.env.VITE_APP_BASE_API as string) || ''
  const path = `${DEFAULT_SSE_BASE}/pending/${encodeURIComponent(threadId)}`
  if (base) return `${base}${path}`
  return path
}

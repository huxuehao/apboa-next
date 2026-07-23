import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'
import type { CostOverviewVO, CostSessionBillRow, CostSessionDetailVO } from '@/types'

const BASE = '/api/agent/cost'

export interface CostRangeQuery {
  /** yyyy-MM-dd，含边界；缺省近 30 天 */
  startDate?: string
  endDate?: string
  agentId?: string
}

/**
 * 概览看板聚合
 * GET /agent/cost/overview
 */
export function overview(query: CostRangeQuery) {
  return request.get<ApiResponse<CostOverviewVO>>(`${BASE}/overview`, { params: query })
}

/**
 * 会话账单分页
 * GET /agent/cost/sessions
 */
export function pageSessions(query: CostRangeQuery & { current: number; size: number; orderBy?: 'cost' | 'time' }) {
  return request.get<ApiResponse<PageResult<CostSessionBillRow>>>(`${BASE}/sessions`, { params: query })
}

/**
 * 单会话逐轮成本明细（实际发生口径，含废弃分支标记）
 * GET /agent/cost/session/{sessionId}
 */
export function sessionDetail(sessionId: string) {
  return request.get<ApiResponse<CostSessionDetailVO>>(`${BASE}/session/${sessionId}`)
}

/**
 * 重算历史成本（补配/改错价后按模型当前单价刷新区间流水，仅管理员）
 * POST /agent/cost/recalculate
 */
export function recalculate(query: CostRangeQuery & { modelConfigId?: string }) {
  return request.post<ApiResponse<number>>(`${BASE}/recalculate`, null, { params: query })
}

/**
 * 存量消息回填流水（扫主表+归档表 assistant 消息 meta，幂等，仅管理员）
 * POST /agent/cost/backfill
 */
export function backfill() {
  return request.post<ApiResponse<Record<string, number>>>(`${BASE}/backfill`)
}

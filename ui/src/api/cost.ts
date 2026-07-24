import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'
import type {
  CostExecutionBillRow,
  CostModelPricingRow,
  CostOverviewVO,
  CostSessionBillRow,
  CostSessionDetailVO,
  CostWorkflowDetailVO
} from '@/types'

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

/** 统一执行账单分页（对话 / 工作流；定时任务后续按同一契约接入）。 */
export function pageBills(query: CostRangeQuery & { current: number; size: number; orderBy?: 'cost' | 'time' }) {
  return request.get<ApiResponse<PageResult<CostExecutionBillRow>>>(`${BASE}/bills`, { params: query })
}

/**
 * 单会话逐轮成本明细（实际发生口径，含废弃分支标记）
 * GET /agent/cost/session/{sessionId}
 */
export function sessionDetail(sessionId: string) {
  return request.get<ApiResponse<CostSessionDetailVO>>(`${BASE}/session/${sessionId}`)
}

/** 工作流单次运行成本详情。 */
export function workflowDetail(runId: string) {
  return request.get<ApiResponse<CostWorkflowDetailVO>>(`${BASE}/workflow/${runId}`)
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

/**
 * 模型配价列表（LLM 价格 + 近30天用量，未配价在前）
 * GET /agent/cost/model-pricing
 */
export function modelPricingList() {
  return request.get<ApiResponse<CostModelPricingRow[]>>(`${BASE}/model-pricing`)
}

/**
 * 轻量改价（只更新两个单价列）
 * PUT /agent/cost/model-pricing/{modelConfigId}
 */
export function updateModelPricing(modelConfigId: string, inputPrice: number | null, outputPrice: number | null) {
  return request.put<ApiResponse<unknown>>(`${BASE}/model-pricing/${modelConfigId}`, null, {
    params: { inputPrice, outputPrice }
  })
}

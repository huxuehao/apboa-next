import request from '@/utils/request'
import type { ApiResponse, PageResult, PageParams } from '@/types/common'
import type {
  DashboardDatasetEntity,
  DashboardEntity,
  DashboardHistoryEntity,
  DashboardUserEntity,
  DatasetExecuteResult,
  DatasetType,
  HttpDatasetConfig,
  PortalDashboard,
} from '@/types/dashboard'

/** 数据集预览执行请求 */
export interface DatasetPreviewRequest {
  sql?: string
  type?: DatasetType
  params?: Record<string, unknown>
  limit?: number
  datasourceId?: string
  httpConfig?: HttpDatasetConfig
}

/** 面板取数请求 */
export interface DatasetQueryRequest {
  params?: Record<string, unknown>
  limit?: number
}

// ── 模板 ──
export function dashboardPage(query: PageParams & Partial<DashboardEntity>) {
  return request.get<ApiResponse<PageResult<DashboardEntity>>>('/api/dashboard/page', { params: query })
}

export function dashboardList(query?: Partial<DashboardEntity>) {
  return request.get<ApiResponse<DashboardEntity[]>>('/api/dashboard', { params: query })
}

export function dashboardGet(id: string) {
  return request.get<ApiResponse<DashboardEntity>>(`/api/dashboard/${id}`)
}

export function dashboardSave(entity: DashboardEntity) {
  return request.post<ApiResponse<DashboardEntity>>('/api/dashboard', entity)
}

export function dashboardUpdate(entity: DashboardEntity) {
  return request.put<ApiResponse<boolean>>('/api/dashboard', entity)
}

export function dashboardRemove(ids: string[], force = 0) {
  return request.delete<ApiResponse<boolean>>(`/api/dashboard/${force}`, { data: ids })
}

export function dashboardSetDefault(id: string) {
  return request.put<ApiResponse<boolean>>(`/api/dashboard/${id}/default`)
}

export function dashboardEnable(id: string, enable: number) {
  return request.put<ApiResponse<boolean>>(`/api/dashboard/${id}/enable/${enable}`)
}

// ── 门户与个人化 ──
export function dashboardPortal() {
  return request.get<ApiResponse<PortalDashboard>>('/api/dashboard/portal')
}

export function dashboardGetPersonal(id: string) {
  return request.get<ApiResponse<DashboardUserEntity>>(`/api/dashboard/${id}/personal`)
}

export function dashboardSavePersonal(id: string, config: unknown) {
  return request.put<ApiResponse<boolean>>(`/api/dashboard/${id}/personal`, config)
}

// ── 历史版本 ──
export function dashboardSaveVersion(id: string, config: unknown, note?: string) {
  return request.post<ApiResponse<boolean>>(`/api/dashboard/${id}/history`, { config, note })
}

export function dashboardHistoryList(id: string) {
  return request.get<ApiResponse<DashboardHistoryEntity[]>>(`/api/dashboard/${id}/history`)
}

export function dashboardRollback(
  id: string,
  historyId: string,
  payload: { snapshotCurrent: boolean; note?: string },
) {
  return request.post<ApiResponse<unknown>>(`/api/dashboard/${id}/history/${historyId}/rollback`, payload)
}

export function dashboardDeleteHistory(id: string, historyId: string) {
  return request.delete<ApiResponse<boolean>>(`/api/dashboard/${id}/history/${historyId}`)
}

// ── 数据集 ──
export function datasetPage(query: PageParams & Partial<DashboardDatasetEntity>) {
  return request.get<ApiResponse<PageResult<DashboardDatasetEntity>>>('/api/dashboard/dataset/page', {
    params: query,
  })
}

export function datasetList(query?: Partial<DashboardDatasetEntity>) {
  return request.get<ApiResponse<DashboardDatasetEntity[]>>('/api/dashboard/dataset', { params: query })
}

export function datasetGet(id: string) {
  return request.get<ApiResponse<DashboardDatasetEntity>>(`/api/dashboard/dataset/${id}`)
}

export function datasetSave(entity: DashboardDatasetEntity) {
  return request.post<ApiResponse<DashboardDatasetEntity>>('/api/dashboard/dataset', entity)
}

export function datasetUpdate(entity: DashboardDatasetEntity) {
  return request.put<ApiResponse<boolean>>('/api/dashboard/dataset', entity)
}

export function datasetRemove(ids: string[]) {
  return request.delete<ApiResponse<boolean>>('/api/dashboard/dataset', { data: ids })
}

export function datasetEnable(id: string, enable: number) {
  return request.put<ApiResponse<boolean>>(`/api/dashboard/dataset/${id}/enable/${enable}`)
}

export function datasetExecute(payload: DatasetPreviewRequest) {
  return request.post<ApiResponse<DatasetExecuteResult>>('/api/dashboard/dataset/execute', payload)
}

export function datasetQuery(id: string, payload: DatasetQueryRequest) {
  return request.post<ApiResponse<DatasetExecuteResult>>(`/api/dashboard/dataset/${id}/query`, payload)
}

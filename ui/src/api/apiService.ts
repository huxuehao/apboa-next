import request from '@/utils/request'
import type { ApiResponse } from '@/types'
import type {
  GatewayApp,
  GatewayApi,
  GatewayAccessLog,
  GatewayPageResult
} from '@/types/apiService'

/**
 * API服务（网关）接口封装
 * 管理面接口由 console 服务承载，统一前缀 /api/gateway
 *
 * @author huxuehao
 */

// ==================== 应用 ====================

/** 分页查询应用 */
export function pageApps(params: { page: number; size: number; name?: string }) {
  return request.get<ApiResponse<GatewayPageResult<GatewayApp>>>('/api/gateway/app/page', { params })
}

/** 查询全部应用（下拉选择用） */
export function listApps() {
  return request.get<ApiResponse<GatewayApp[]>>('/api/gateway/app')
}

/** 新增应用 */
export function addApp(app: GatewayApp) {
  return request.post<ApiResponse<boolean>>('/api/gateway/app', app)
}

/** 更新应用 */
export function updateApp(app: GatewayApp) {
  return request.put<ApiResponse<boolean>>('/api/gateway/app', app)
}

/** 删除应用 */
export function deleteApps(ids: string[]) {
  return request.delete<ApiResponse<boolean>>('/api/gateway/app', { data: ids })
}

/** 应用上下线 */
export function toggleAppOnline(id: string, v: number) {
  return request.put<ApiResponse<boolean>>(`/api/gateway/app/${id}/online/${v}`)
}

// ==================== API ====================

/** 分页查询API */
export function pageApis(params: {
  page: number
  size: number
  name?: string
  category?: string
  appId?: string
  online?: number
}) {
  return request.get<ApiResponse<GatewayPageResult<GatewayApi>>>('/api/gateway/api/page', { params })
}

/** API详情 */
export function getApi(id: string) {
  return request.get<ApiResponse<GatewayApi>>(`/api/gateway/api/${id}`)
}

/** 新增API */
export function addApi(api: GatewayApi) {
  return request.post<ApiResponse<boolean>>('/api/gateway/api', api)
}

/** 更新API */
export function updateApi(api: GatewayApi) {
  return request.put<ApiResponse<boolean>>('/api/gateway/api', api)
}

/** 删除API */
export function deleteApis(ids: string[]) {
  return request.delete<ApiResponse<boolean>>('/api/gateway/api', { data: ids })
}

/** API上下线 */
export function toggleApiOnline(id: string, v: number) {
  return request.put<ApiResponse<boolean>>(`/api/gateway/api/${id}/online/${v}`)
}

/** 查询自维护分类列表 */
export function getCategories() {
  return request.get<ApiResponse<string[]>>('/api/gateway/api/categories')
}

/** 查询API简要列表（日志筛选用） */
export function getBriefApis() {
  return request.get<ApiResponse<GatewayApi[]>>('/api/gateway/api/brief')
}

// ==================== 访问日志 ====================

/** 分页查询访问日志 */
export function pageAccessLogs(params: {
  page: number
  size: number
  apiId?: string
  appId?: string
  status?: number
}) {
  return request.get<ApiResponse<GatewayPageResult<GatewayAccessLog>>>('/api/gateway/access-log/page', { params })
}

/** 访问日志详情 */
export function getAccessLog(id: string) {
  return request.get<ApiResponse<GatewayAccessLog>>(`/api/gateway/access-log/${id}`)
}

/** 删除访问日志 */
export function deleteAccessLogs(ids: string[]) {
  return request.delete<ApiResponse<boolean>>('/api/gateway/access-log', { data: ids })
}

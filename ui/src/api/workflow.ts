import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types/common'
import type {
  NodeMetadata,
  Workflow,
  WorkflowDetail,
  WorkflowNodeExecution,
  WorkflowQuery,
  WorkflowResource,
  WorkflowRun,
  WorkflowRunQuery,
  WorkflowRunRequest,
  WorkflowRunResult,
  WorkflowValidationResult,
  WorkflowVersion,
} from '@/types/workflow'

export function workflowPage(query: WorkflowQuery) {
  return request.get<ApiResponse<PageResult<Workflow>>>('/api/workflow/page', { params: query })
}

export function workflowDetail(id: string) {
  return request.get<ApiResponse<WorkflowDetail>>(`/api/workflow/${id}`)
}

export function workflowSave(entity: Workflow) {
  return request.post<ApiResponse<Workflow>>('/api/workflow', entity)
}

export function workflowUpdate(entity: Workflow) {
  return request.put<ApiResponse<boolean>>('/api/workflow', entity)
}

export function workflowRemove(ids: string[], force = 0) {
  return request.delete<ApiResponse<boolean>>(`/api/workflow/${force}`, { data: ids })
}

export function usedWithAgent(ids: string[]) {
  return request.post<ApiResponse<unknown[]>>('/api/workflow/used-with-agent', ids)
}

export function workflowCopy(id: string) {
  return request.post<ApiResponse<Workflow>>(`/api/workflow/${id}/copy`)
}

export function workflowLock(id: string, locked: number) {
  return request.put<ApiResponse<boolean>>(`/api/workflow/${id}/lock/${locked}`)
}

export function workflowValidate(id: string) {
  return request.post<ApiResponse<WorkflowValidationResult>>(`/api/workflow/${id}/validate`)
}

export function workflowPublish(id: string, remark?: string) {
  return request.post<ApiResponse<WorkflowVersion>>(`/api/workflow/${id}/publish`, null, {
    params: { remark },
  })
}

export function workflowVersions(id: string) {
  return request.get<ApiResponse<WorkflowVersion[]>>(`/api/workflow/${id}/versions`)
}

export function workflowRollback(id: string, version: string) {
  return request.post<ApiResponse<Workflow>>(`/api/workflow/${id}/versions/${version}/rollback`)
}

export function workflowDeleteVersion(id: string, version: string) {
  return request.delete<ApiResponse<boolean>>(`/api/workflow/${id}/versions/${version}`)
}

export function workflowDebugRun(id: string, payload: WorkflowRunRequest) {
  return request.post<ApiResponse<WorkflowRunResult>>(`/api/runtime/workflow/${id}/debug-run`, payload)
}

export function workflowRun(id: string, payload: WorkflowRunRequest) {
  return request.post<ApiResponse<WorkflowRunResult>>(`/api/runtime/workflow/${id}/run`, payload)
}

export function workflowRunPage(query: WorkflowRunQuery) {
  return request.get<ApiResponse<PageResult<WorkflowRun>>>('/api/workflow/runs/page', { params: query })
}

export function workflowRunDetail(runId: string) {
  return request.get<ApiResponse<WorkflowRun>>(`/api/workflow/runs/${runId}`)
}

export function workflowRunNodes(runId: string) {
  return request.get<ApiResponse<WorkflowNodeExecution[]>>(`/api/workflow/runs/${runId}/nodes`)
}

export function workflowNodeMetadata() {
  return request.get<ApiResponse<NodeMetadata[]>>('/api/workflow/node-metadata')
}

export function enabledCaches() {
  return request.get<ApiResponse<WorkflowResource[]>>('/api/cache', { params: { enabled: 1 } })
}

export function enabledDatasources() {
  return request.get<ApiResponse<WorkflowResource[]>>('/api/datasource', { params: { enabled: 1 } })
}

export function enabledMqs() {
  return request.get<ApiResponse<WorkflowResource[]>>('/api/mq', { params: { enabled: 1 } })
}

export function enabledChannels() {
  return request.get<ApiResponse<WorkflowResource[]>>('/api/channel', { params: { enabled: 1 } })
}

export function checkCacheConnect(resource: WorkflowResource) {
  return request.post<ApiResponse<boolean>>('/api/cache/check/connect', resource)
}

export function checkDatasourceConnect(resource: WorkflowResource) {
  return request.post<ApiResponse<boolean>>('/api/datasource/check/connect', resource)
}

export function checkMqConnect(resource: WorkflowResource) {
  return request.post<ApiResponse<boolean>>('/api/mq/check/connect', resource)
}

export function checkChannelConnect(resource: WorkflowResource) {
  return request.post<ApiResponse<boolean>>('/api/channel/check/connect', resource)
}

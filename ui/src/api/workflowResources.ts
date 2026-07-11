import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types/common'
import type {
  CacheResource,
  DatasourceResource,
  MqResource,
  WorkflowManagedResource,
  WorkflowResourceKind,
  WorkflowResourceQuery,
  WorkflowResourceSummary
} from '@/types/workflowResources'

const endpointMap: Record<WorkflowResourceKind, string> = {
  datasource: '/api/datasource',
  cache: '/api/cache',
  mq: '/api/mq'
}

export type ResourceByKind<T extends WorkflowResourceKind> = T extends 'datasource'
  ? DatasourceResource
  : T extends 'cache'
    ? CacheResource
    : MqResource

export function summary() {
  return request.get<ApiResponse<WorkflowResourceSummary>>('/api/workflow/resources/summary')
}

export function page<T extends WorkflowResourceKind>(kind: T, query: WorkflowResourceQuery) {
  return request.get<ApiResponse<PageResult<ResourceByKind<T>>>>(`${endpointMap[kind]}/page`, {
    params: query
  })
}

export function save(kind: WorkflowResourceKind, entity: WorkflowManagedResource) {
  return request.post<ApiResponse<boolean>>(endpointMap[kind], entity)
}

export function update(kind: WorkflowResourceKind, entity: WorkflowManagedResource) {
  return request.put<ApiResponse<boolean>>(endpointMap[kind], entity)
}

export function remove(kind: WorkflowResourceKind, ids: string[], force = 0) {
  return request.delete<ApiResponse<boolean>>(`${endpointMap[kind]}/${force}`, { data: ids })
}

export function enable(kind: WorkflowResourceKind, id: string, enabled: boolean) {
  return request.put<ApiResponse<boolean>>(`${endpointMap[kind]}/${id}/enable/${enabled ? 1 : 0}`)
}

export function checkConnect(kind: WorkflowResourceKind, resource: WorkflowManagedResource) {
  return request.post<ApiResponse<boolean>>(`${endpointMap[kind]}/check/connect`, resource)
}

export function checkSavedConnect(kind: WorkflowResourceKind, id: string) {
  return request.post<ApiResponse<boolean>>(`${endpointMap[kind]}/${id}/check/connect`)
}

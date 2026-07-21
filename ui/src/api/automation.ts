import request from '@/utils/request'
import type { ApiResponse } from '@/types'
import type { JobInfo } from '@/types'
import type { JobRecordVO, JobPageParams } from '@/types/automation'

/**
 * 分页查询自动化任务
 * GET /runtime/job/page
 */
export function pageJobs(params: JobPageParams) {
  return request.get<ApiResponse<{
    records: JobInfo[]
    total: number
    size: number
    current: number
    pages: number
  }>>('/api/runtime/job/page', {
    params: {
      page: params.page,
      size: params.size,
      type: params.type || undefined,
      keyword: params.keyword || undefined
    }
  })
}

/**
 * 新增自动化任务
 * POST /runtime/job/add
 */
export function addJob(jobInfo: JobInfo) {
  return request.post<ApiResponse<boolean>>('/api/runtime/job/add', jobInfo)
}

/**
 * 更新自动化任务
 * POST /runtime/job/update
 */
export function updateJob(jobInfo: JobInfo) {
  return request.post<ApiResponse<boolean>>('/api/runtime/job/update', jobInfo)
}

/**
 * 删除自动化任务
 * GET /runtime/job/delete
 */
export function deleteJob(id: string) {
  return request.get<ApiResponse<boolean>>('/api/runtime/job/delete', {
    params: { id }
  })
}

/**
 * 启用/禁用切换
 * GET /runtime/job/toggle
 */
export function toggleJob(id: string) {
  return request.get<ApiResponse<boolean>>('/api/runtime/job/toggle', {
    params: { id }
  })
}

/**
 * 启动定时任务
 * GET /runtime/job/start
 */
export function startJob(id: string) {
  return request.get<ApiResponse<boolean>>('/api/runtime/job/start', {
    params: { id }
  })
}

/**
 * 停止定时任务
 * GET /runtime/job/stop
 */
export function stopJob(id: string) {
  return request.get<ApiResponse<boolean>>('/api/runtime/job/stop', {
    params: { id }
  })
}

/**
 * 手动触发一次执行
 * GET /runtime/job/trigger
 */
export function triggerJob(id: string) {
  return request.get<ApiResponse<boolean>>('/api/runtime/job/trigger', {
    params: { id }
  })
}

/**
 * 获取运行记录（分页）
 * GET /runtime/job/records
 */
export function getRecords(jobId: string, page: number = 1, size: number = 50) {
  return request.get<ApiResponse<{
    records: JobRecordVO[]
    total: number
    size: number
    current: number
    pages: number
  }>>('/api/runtime/job/records', {
    params: { jobId, page, size }
  })
}

/**
 * 根据ID查询详情
 * GET /runtime/job/{id}
 */
export function getJobById(id: string) {
  return request.get<ApiResponse<JobInfo>>(`/api/runtime/job/${id}`)
}

/**
 * 获取 Agent 运行详情（对话消息）
 * GET /runtime/job/agent-detail
 */
export function getAgentDetail(recordId: number) {
  return request.get<ApiResponse<unknown[]>>('/api/runtime/job/agent-detail', {
    params: { recordId }
  })
}

/**
 * 获取 Workflow 运行详情（节点日志）
 * GET /runtime/job/workflow-detail
 */
export function getWorkflowDetail(recordId: number) {
  return request.get<ApiResponse<unknown>>('/api/runtime/job/workflow-detail', {
    params: { recordId }
  })
}

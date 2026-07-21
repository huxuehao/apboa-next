import request from '@/utils/request'
import type { ApiResponse } from '@/types'
import type { WorkspaceFileNode, WorkspaceCapacityVO } from '@/types'

const BASE = '/api/runtime/workspace'

/**
 * 上传单个文件到工作空间
 * POST /runtime/workspace/upload
 */
export function upload(sessionId: string, file: File) {
  const formData = new FormData()
  formData.append('sessionId', sessionId)
  formData.append('file', file)
  return request.post<ApiResponse<string>>(`${BASE}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 上传多个文件到工作空间
 * POST /runtime/workspace/upload/batch
 */
export function uploadBatch(sessionId: string, files: File[]) {
  const formData = new FormData()
  formData.append('sessionId', sessionId)
  files.forEach(file => formData.append('files', file))
  return request.post<ApiResponse<string[]>>(`${BASE}/upload/batch`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 上传压缩包并自动解压到工作空间
 * POST /runtime/workspace/upload/archive
 */
export function uploadArchive(sessionId: string, file: File) {
  const formData = new FormData()
  formData.append('sessionId', sessionId)
  formData.append('file', file)
  return request.post<ApiResponse<string[]>>(`${BASE}/upload/archive`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 获取工作空间文件树
 * GET /runtime/workspace/files
 */
export function listFiles(sessionId: string) {
  return request.get<ApiResponse<WorkspaceFileNode[]>>(`${BASE}/files`, {
    params: { sessionId }
  })
}

/**
 * 下载工作空间中的单个文件
 * GET /runtime/workspace/download
 */
export function download(sessionId: string, fileName: string) {
  return request.get(`${BASE}/download`, {
    params: { sessionId, fileName },
    responseType: 'blob'
  })
}

/**
 * 下载工作空间中的多个文件（打包成ZIP）
 * POST /runtime/workspace/download/batch
 */
export function downloadBatch(sessionId: string, filePaths: string[]) {
  return request.post(`${BASE}/download/batch`, filePaths, {
    params: { sessionId },
    responseType: 'blob'
  })
}

/**
 * 下载整个工作空间（打包成ZIP）
 * GET /runtime/workspace/download/all
 */
export function downloadAll(sessionId: string) {
  return request.get(`${BASE}/download/all`, {
    params: { sessionId },
    responseType: 'blob'
  })
}

/**
 * 删除工作空间中的单个文件
 * DELETE /runtime/workspace/file
 */
export function deleteFile(sessionId: string, filePath: string) {
  return request.delete<ApiResponse<void>>(`${BASE}/file`, {
    params: { sessionId, filePath }
  })
}

/**
 * 清空工作空间下的所有文件
 * DELETE /runtime/workspace/clear
 */
export function clearWorkspace(sessionId: string) {
  return request.delete<ApiResponse<void>>(`${BASE}/clear`, {
    params: { sessionId }
  })
}

/**
 * 获取工作空间容量信息
 * GET /runtime/workspace/capacity
 */
export function getCapacity(sessionId: string) {
  return request.get<ApiResponse<WorkspaceCapacityVO>>(`${BASE}/capacity`, {
    params: { sessionId }
  })
}

/**
 * 检查工作空间是否存在（仅检查目录存在性，不会自动创建）
 * GET /runtime/workspace/exists
 */
export function workspaceExists(sessionId: string) {
  return request.get<ApiResponse<boolean>>(`${BASE}/exists`, {
    params: { sessionId }
  })
}

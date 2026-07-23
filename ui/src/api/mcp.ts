import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'
import type { McpServerDTO, McpServerVO, McpToolDebugResultVO, McpToolVO } from '@/types'
import type { McpServer } from '@/types'

/**
 * 分页查询
 * GET /mcp/server/page
 */
export function page(query: McpServerDTO) {
  return request.get<ApiResponse<PageResult<McpServerVO>>>('/api/mcp/server/page', {
    params: query
  })
}

/**
 * 详情
 * GET /mcp/server/{id}
 */
export function detail(id: string) {
  return request.get<ApiResponse<McpServerVO>>(`/api/mcp/server/${id}`)
}

/**
 * 新增
 * POST /mcp/server
 */
export function save(entity: McpServer) {
  return request.post<ApiResponse<McpServerVO>>('/api/mcp/server', entity)
}

/**
 * 修改
 * PUT /mcp/server
 */
export function update(entity: McpServer) {
  return request.put<ApiResponse<McpServerVO>>('/api/mcp/server', entity)
}

/**
 * 连接并加载工具目录
 * POST /mcp/server/{id}/activate
 */
export function activate(id: string) {
  return request.post<ApiResponse<McpServerVO>>(`/api/mcp/server/${id}/activate`)
}

/**
 * 刷新工具目录
 * POST /mcp/server/{id}/sync-tools
 */
export function syncTools(id: string) {
  return request.post<ApiResponse<McpServerVO>>(`/api/mcp/server/${id}/sync-tools`)
}

/**
 * 工具列表
 * GET /mcp/server/{id}/tools
 */
export function listTools(id: string) {
  return request.get<ApiResponse<McpToolVO[]>>(`/api/mcp/server/${id}/tools`)
}

/**
 * 批量切换工具全局可用状态
 * PUT /mcp/server/{id}/tools/global-enabled
 */
export function updateToolsGlobalEnabled(id: string, toolIds: string[], enabled: boolean) {
  return request.put<ApiResponse<McpServerVO>>(`/api/mcp/server/${id}/tools/global-enabled`, {
    toolIds,
    enabled
  })
}

/**
 * 批量设置工具是否需要人工确认（HITL §6.6）
 * PUT /mcp/server/{id}/tools/global-need-confirm
 */
export function updateToolsNeedConfirm(id: string, toolIds: string[], needConfirm: boolean) {
  return request.put<ApiResponse<McpServerVO>>(`/api/mcp/server/${id}/tools/global-need-confirm`, {
    toolIds,
    needConfirm
  })
}

/**
 * 删除
 * DELETE /mcp/server
 */
export function remove(ids: string[]) {
  return request.delete<ApiResponse<boolean>>('/api/mcp/server', { data: ids })
}

/**
 * 被哪些Agent使用
 * POST /mcp/server/used-with-agent
 */
export function usedWithAgent(ids: string[]) {
  return request.post<ApiResponse<unknown[]>>('/api/mcp/server/used-with-agent', ids)
}

/**
 * 调试调用工具
 * POST /mcp/server/tools/debug
 */
export function debugTool(toolId: string | number, input: Record<string, unknown>) {
  return request.post<ApiResponse<McpToolDebugResultVO>>('/api/mcp/server/tools/debug', {
    toolId,
    input
  })
}

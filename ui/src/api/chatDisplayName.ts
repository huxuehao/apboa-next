import request from '@/utils/request'
import type { ApiResponse, ChatDisplayNameVO } from '@/types'

/**
 * 查询对话显示名映射（子智能体/工具/MCP 工具三张名称表）
 * GET /agent/chat/display-name
 */
export function get() {
  return request.get<ApiResponse<ChatDisplayNameVO>>('/api/agent/chat/display-name')
}

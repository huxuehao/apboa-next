import request from '@/utils/request'
import type {
  ApiResponse,
  PageResult,
  SkillPackage,
  ToolConfig,
} from '@/types'
import type { AgentDefinitionDTO, AgentDefinitionVO, AgentChatContextVO } from '@/types'

/**
 * 分页查询
 * GET /agent/definition/page
 */
export function page(query: AgentDefinitionDTO) {
  return request.get<ApiResponse<PageResult<AgentDefinitionVO>>>('/api/agent/definition/page', {
    params: query
  })
}

/**
 * 详情
 * GET /agent/definition/{id}
 */
export function detail(id: string) {
  return request.get<ApiResponse<AgentDefinitionVO>>(`/api/agent/definition/${id}`)
}

/**
 * 对话页面专用聚合详情（detail+avatar+allowFileType+enabledTools+enabledSkills 合一）
 * GET /agent/definition/{id}/chat-context
 */
export function chatContext(id: string) {
  return request.get<ApiResponse<AgentChatContextVO>>(`/api/agent/definition/${id}/chat-context`)
}

/**
 * 新增
 * POST /agent/definition
 */
export function save(vo: AgentDefinitionVO) {
  return request.post<ApiResponse<AgentDefinitionVO>>('/api/agent/definition', vo)
}

/**
 * 修改
 * PUT /agent/definition
 */
export function update(vo: AgentDefinitionVO) {
  return request.put<ApiResponse<boolean>>('/api/agent/definition', vo)
}

/**
 * 删除
 * DELETE /agent/definition
 */
export function remove(ids: string[]) {
  return request.delete<ApiResponse<boolean>>('/api/agent/definition', { data: ids })
}

/**
 * 被哪些Agent使用
 * POST /agent/definition/used-with-agent
 */
export function usedWithAgent(ids: string[]) {
  return request.post<ApiResponse<unknown[]>>('/api/agent/definition/used-with-agent', ids)
}

/**
 * 获取所有Tag
 * GET /api/agent/definition/get/tags
 */
export function listTags() {
  return request.get<ApiResponse<string[]>>('/api/agent/definition/get/tags')
}

/**
 * 获取所有Tag
 * GET /api/agent/definition/get/tags
 */
export function allowFileType(id: string) {
  return request.get<ApiResponse<string[]>>(`/api/agent/definition/${id}/allow/file-type`)
}

/**
 * 获取智能体头像（base64 data URL，未设置返回 null；独立于 VO 接口）
 * GET /agent/definition/{id}/avatar
 */
export function getAvatar(id: string) {
  return request.get<ApiResponse<string | null>>(`/api/agent/definition/${id}/avatar`)
}

/**
 * 更新智能体头像（avatar 传空清除）
 * PUT /agent/definition/{id}/avatar
 */
export function updateAvatar(id: string, avatar: string | null) {
  return request.put<ApiResponse<boolean>>(`/api/agent/definition/${id}/avatar`, { avatar })
}


/**
 * 执行工具
 */
export function agentDoTool(toolName: string, args: any) {
  return request.post<ApiResponse<any>>(`/api/runtime/agent/do/${toolName}/tool`, args)
}

/**
 * 获取Agent启用的工具
 */
export function enabledToolsOfAgent(agentId: string) {
  return request.get<ApiResponse<ToolConfig[]>>(`/api/agent/definition/${agentId}/enabled/tools`)
}

/**
 * 获取Agent启用的技能包
 */
export function enabledSkillsOfAgent(agentId: string) {
  return request.get<ApiResponse<SkillPackage[]>>(`/api/agent/definition/${agentId}/enabled/skills`)
}

/**
 * 获取Agent启用的工作流
 */
export function enabledWorkflowsOfAgent(agentId: string) {
  return request.get<ApiResponse<unknown[]>>(`/api/agent/definition/${agentId}/enabled/workflows`)
}

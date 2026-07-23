import request from '@/utils/request'
import type { ApiResponse } from '@/types'

/**
 * 获取智能体对话Key
 * GET /agent/chat-key/{agentId}
 *
 * @param agentId 智能体ID
 * @param refresh 是否刷新Key
 */
export function getChatKey(agentId: string | number, refresh: boolean = false) {
  return request.get<ApiResponse<string>>(`/api/agent/chat-key/${agentId}`, {
    params: { refresh }
  })
}

export function getAgentIdByChatKey(chatKey: string) {
  return request.get<ApiResponse<string>>(`/api/agent/chat-key/${chatKey}/get-agent-id`)
}

/**
 * 查询嵌入身份密钥（embedSecret）。未启用返回 null。
 * 仅平台登录用户可访问（免登通道拿到 secret 即可伪造任意外部用户）
 */
export function getEmbedSecret(agentId: string | number) {
  return request.get<ApiResponse<string | null>>(`/api/agent/chat-key/${agentId}/embed-secret`)
}

/**
 * 生成/轮换嵌入身份密钥：新密钥生效，旧密钥转入观察期（新旧双活可验签）
 */
export function rotateEmbedSecret(agentId: string | number) {
  return request.post<ApiResponse<string>>(`/api/agent/chat-key/${agentId}/embed-secret/rotate`)
}

/**
 * 停用嵌入身份验证（双密钥一并置空）。停用后带 userJwt 的换 token 请求被拒，
 * 嵌入访客退回纯匿名
 */
export function disableEmbedSecret(agentId: string | number) {
  return request.delete<ApiResponse<boolean>>(`/api/agent/chat-key/${agentId}/embed-secret`)
}

import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'
import type {
  ChatSessionCreateDTO,
  ChatMessageAppendDTO,
  ChatSessionQueryDTO,
  ChatSessionVO,
  ChatMessageVO
} from '@/types'

const BASE = '/api/agent/chat/session'

/**
 * 创建新会话
 * POST /agent/chat/session
 */
export function createSession(dto: ChatSessionCreateDTO) {
  return request.post<ApiResponse<ChatSessionVO>>(BASE, dto)
}

/**
 * 正常对话追加消息
 * POST /agent/chat/session/{sessionId}/message
 */
export function appendMessage(sessionId: string, dto: ChatMessageAppendDTO) {
  return request.post<ApiResponse<ChatMessageVO>>(`${BASE}/${sessionId}/message`, dto)
}

/**
 * 重新生成（新分支）
 * POST /agent/chat/session/{sessionId}/regenerate
 */
export function regenerateMessage(sessionId: string, dto: ChatMessageAppendDTO) {
  return request.post<ApiResponse<ChatMessageVO>>(`${BASE}/${sessionId}/regenerate`, dto)
}

/**
 * 切换历史分支
 * PUT /agent/chat/session/{sessionId}/current?messageId=xxx
 */
export function switchCurrentMessage(sessionId: string, messageId: string) {
  return request.put<ApiResponse<unknown>>(`${BASE}/${sessionId}/current`, null, {
    params: { messageId }
  })
}

/**
 * 回显当前完整对话
 * GET /agent/chat/session/{sessionId}/messages/current
 */
export function getCurrentMessages(sessionId: string) {
  return request.get<ApiResponse<ChatMessageVO[]>>(`${BASE}/${sessionId}/messages/current`)
}

/**
 * 分页加载当前对话消息（向上滚动加载历史）
 * GET /agent/chat/session/{sessionId}/messages/paged?beforeDepth=xx&size=50
 */
export function getCurrentMessagesPaged(
  sessionId: string,
  params: { beforeDepth?: number | null; size?: number }
) {
  return request.get<ApiResponse<{
    messages: ChatMessageVO[]
    hasMore: boolean
    nextBeforeDepth: number | null
  }>>(`${BASE}/${sessionId}/messages/paged`, { params })
}

/**
 * 会话列表
 * GET /agent/chat/session/list
 */
export function listSessions(query?: ChatSessionQueryDTO) {
  return request.get<ApiResponse<ChatSessionVO[]>>(`${BASE}/list`, {
    params: query
  })
}

/**
 * 分页查询会话
 * GET /agent/chat/session/page
 */
export function pageSessions(query?: ChatSessionQueryDTO) {
  return request.get<ApiResponse<PageResult<ChatSessionVO>>>(`${BASE}/page`, {
    params: query
  })
}

/**
 * 会话详情
 * GET /agent/chat/session/{id}
 */
export function getSessionDetail(id: string) {
  return request.get<ApiResponse<ChatSessionVO>>(`${BASE}/${id}`)
}

/** 会话 HITL 授权模式：一键授权（自动允许）/ 逐步确认（人工决策）/ 拒绝授权（自动拒绝） */
export type ConfirmMode = 'AUTO_APPROVE' | 'MANUAL' | 'AUTO_REJECT'

/**
 * 查询会话 HITL 授权模式（无记录=MANUAL 逐步确认）
 * GET /agent/chat/session/{sessionId}/confirm-mode
 */
export function getConfirmMode(sessionId: string) {
  return request.get<ApiResponse<ConfirmMode>>(`${BASE}/${sessionId}/confirm-mode`)
}

/**
 * 设置会话 HITL 授权模式
 * PUT /agent/chat/session/{sessionId}/confirm-mode?mode=
 */
export function setConfirmMode(sessionId: string, mode: ConfirmMode) {
  return request.put<ApiResponse<boolean>>(`${BASE}/${sessionId}/confirm-mode?mode=${mode}`)
}

/**
 * 查询会话思考模式有效值（会话覆盖 ?? 默认开；仅 thinkingSwitchSupported 的模型有意义）
 * GET /agent/chat/session/{sessionId}/thinking-mode
 */
export function getThinkingMode(sessionId: string) {
  return request.get<ApiResponse<boolean>>(`${BASE}/${sessionId}/thinking-mode`)
}

/**
 * 设置会话思考模式（下一条消息生效——后端检测变化重建 agent）
 * PUT /agent/chat/session/{sessionId}/thinking-mode?enabled=
 */
export function setThinkingMode(sessionId: string, enabled: boolean) {
  return request.put<ApiResponse<boolean>>(`${BASE}/${sessionId}/thinking-mode?enabled=${enabled}`)
}

/**
 * 置顶会话
 * PUT /agent/chat/session/{id}/pin
 */
export function pinSession(id: string) {
  return request.put<ApiResponse<unknown>>(`${BASE}/${id}/pin`)
}

/**
 * 取消置顶会话
 * PUT /agent/chat/session/{id}/unpin
 */
export function unpinSession(id: string) {
  return request.put<ApiResponse<unknown>>(`${BASE}/${id}/unpin`)
}

/**
 * 更新会话标题
 * PUT /agent/chat/session/{id}/title?title=xxx
 */
export function updateSessionTitle(id: string, title: string) {
  return request.put<ApiResponse<unknown>>(`${BASE}/${id}/title`, null, {
    params: { title }
  })
}

/**
 * 更新当前消息内容（后端通过 session 的 current_message_id 定位，调用方无需传 messageId）
 * PUT /agent/chat/session/{sessionId}/current-message/content
 */
export function updateCurrentMessageContent(sessionId: string, content: string) {
  return request.put<ApiResponse<unknown>>(`${BASE}/${sessionId}/current-message/content`, { content })
}

/**
 * 删除会话
 * DELETE /agent/chat/session/{id}
 */
export function deleteSession(id: string) {
  return request.delete<ApiResponse<unknown>>(`${BASE}/${id}`)
}

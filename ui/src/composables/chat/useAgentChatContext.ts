import * as agentApi from '@/api/agent'
import type { AgentChatContextVO } from '@/types'

/**
 * 智能体聊天上下文聚合请求（按 agentId 缓存）
 *
 * useAgentDetail（detail+avatar+allowFileType）与 ChatInputEditor（enabledTools+enabledSkills）
 * 各自独立 watch agentId 发起请求，命中同一 agentId 时复用同一个 Promise，避免两处各发一次请求。
 */
let contextCache: { agentId: string; promise: Promise<AgentChatContextVO | null> } | null = null

export function fetchAgentChatContext(agentId: string): Promise<AgentChatContextVO | null> {
  if (contextCache?.agentId !== agentId) {
    contextCache = {
      agentId,
      promise: agentApi.chatContext(agentId).then((res) => res.data?.data ?? null).catch(() => null)
    }
  }
  return contextCache.promise
}

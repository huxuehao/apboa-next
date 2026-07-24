import { ref, watch } from 'vue'
import type { AgentDefinitionVO } from '@/types'
import { setPageTitle } from '@/router/guards.ts'
import { fetchAgentChatContext } from '@/composables/chat/useAgentChatContext'

export function useAgentDetail(agentId: import('vue').Ref<string>) {
  const agentDetail = ref<AgentDefinitionVO | null>(null)
  const agentAvatar = ref<string | null>(null)
  const allowFileType = ref<string[]>([])

  /**
   * 三者共用一次聚合请求（detail+avatar+allowFileType+enabledTools+enabledSkills 合一，
   * 命中同一 agentId 时与 ChatInputEditor 的请求去重，见 useAgentChatContext）
   */
  const loadAll = async () => {
    if (!agentId.value) return
    const ctx = await fetchAgentChatContext(agentId.value)
    agentDetail.value = ctx?.detail ?? null
    if (agentDetail.value) setPageTitle(agentDetail.value.name)
    agentAvatar.value = ctx?.avatar || null
    allowFileType.value = ctx?.allowFileType ?? []
  }

  watch(agentId, () => {
    loadAll().then(() => {})
  }, { immediate: true })

  return {
    agentDetail,
    agentAvatar,
    allowFileType
  }
}

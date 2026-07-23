import { ref, watch } from 'vue'
import * as agentApi from '@/api/agent'
import type { AgentDefinitionVO } from '@/types'
import { setPageTitle } from '@/router/guards.ts'

export function useAgentDetail(agentId: import('vue').Ref<string>) {
  const agentDetail = ref<AgentDefinitionVO | null>(null)
  const agentAvatar = ref<string | null>(null)
  const allowFileType = ref<string[]>([])

  const loadAgentDetail = async () => {
    if (!agentId.value) return
    try {
      const res = await agentApi.detail(agentId.value)
      agentDetail.value = res.data?.data ?? null
      setPageTitle(agentDetail.value.name)
    } catch {
      agentDetail.value = null
    }
  }

  /**
   * 头像走独立接口（base64 不随 detail 返回），失败静默视为未设置
   */
  const loadAgentAvatar = async () => {
    if (!agentId.value) return
    try {
      const res = await agentApi.getAvatar(agentId.value)
      agentAvatar.value = res.data?.data || null
    } catch {
      agentAvatar.value = null
    }
  }

  const loadAllowFileType = async () => {
    if (!agentId.value) return
    try {
      const res = await agentApi.allowFileType(agentId.value)
      allowFileType.value = res.data?.data ?? []
    } catch {
      allowFileType.value = []
    }
  }

  watch(agentId, () => {
    loadAgentDetail().then(() => {})
    loadAgentAvatar().then(() => {})
    loadAllowFileType().then(() => {})
  }, { immediate: true })

  return {
    agentDetail,
    agentAvatar,
    allowFileType,
    loadAgentDetail,
    loadAgentAvatar,
    loadAllowFileType
  }
}

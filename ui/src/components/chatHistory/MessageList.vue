<script setup lang="ts">
import MessageItem from './MessageItem.vue'
import SubAgentCard from '@/components/chat/SubAgentCard.vue'
import type { DisplayMessage, SubAgentRunVO } from '@/types'

defineProps<{
  messages: DisplayMessage[]
}>()

function subAgentRunFor(message: DisplayMessage): SubAgentRunVO {
  if (message.subAgentRun) return message.subAgentRun
  let anchor: { invocationId?: string; agentTitle?: string; agentCode?: string } = {}
  try { anchor = JSON.parse(message.content || '{}') } catch { /* malformed legacy anchor */ }
  return {
    invocationId: anchor.invocationId || message.id,
    agentTitle: anchor.agentTitle,
    agentCode: anchor.agentCode,
    status: 'SUCCESS',
    events: [],
  }
}

</script>

<template>
  <div class="chat-main-messages">
    <template v-for="msg in messages" :key="msg.id">
      <SubAgentCard
        v-if="msg.role === 'subagent'"
        :run="subAgentRunFor(msg)"
      />
      <MessageItem
        v-else
        :role="msg.role"
        :content="msg.content"
        :created-at="msg.createdAt"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;
</style>

<script setup lang="ts">
import { computed } from 'vue'
import MessageItem from './MessageItem.vue'
import ToolCallItem from './ToolCallItem.vue'
import DayDivider from './DayDivider.vue'
import type { DisplayMessage } from '@/types'
import type {FlatFileItem} from "@/composables/chat/useWorkspaceFiles.ts";
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'

const props = defineProps<{
  messages: DisplayMessage[]
  agentHasResult?: boolean
  toolCalls: Array<{ id: string; name: string; args: string; result?: string; elapsed?: number, needConfirm?: boolean, startTime?: number, subSteps?: Array<Record<string, unknown>> }>
}>()

/**
 * 日期分隔：createdAt 的日期（前 10 位）与上一条不同处标记 dividerDay。
 * 无时间的消息（流式占位等）不参与比较，避免误插。
 */
const wrappedMessages = computed(() => {
  let prevDay: string | null = null
  return props.messages.map((msg) => {
    const day = (msg.createdAt || '').slice(0, 10)
    let dividerDay: string | null = null
    if (day.length === 10 && day !== prevDay) {
      dividerDay = day
      prevDay = day
    }
    return { msg, dividerDay }
  })
})

defineEmits<{
  (e: 'toolContent', value: any): void
  (e: 'inputTagPreview', value: FlatFileItem): void
  (e: 'interactionSubmit', payload: InteractionSubmitPayload): void
  (e: 'uipRetry', uipCode: string): void
  (e: 'vepRetry', vepCode: string): void
}>()
</script>

<template>
  <div class="chat-main-messages">
    <template v-for="(item, index) in wrappedMessages" :key="item.msg.id">
      <DayDivider v-if="item.dividerDay" :day="item.dividerDay" />
      <MessageItem
        @inputTagPreview="$emit('inputTagPreview', $event as FlatFileItem)"
        @interaction-submit="$emit('interactionSubmit', $event)"
        @uip-retry="$emit('uipRetry', $event)"
        @vep-retry="$emit('vepRetry', $event)"
        :id="item.msg.id"
        :current-index="index"
        :total-messages="messages.length"
        :role="item.msg.role"
        :content="item.msg.content"
        :created-at="item.msg.createdAt"
        :meta="item.msg.meta"
        :agent-has-result="agentHasResult"
        :is-streaming="item.msg.isStreaming"
      />
    </template>
    <TransitionGroup name="jelly">
      <ToolCallItem
        v-for="t in toolCalls"
        :key="t.id"
        :id="t.id"
        :name="t.name"
        :args="t.args"
        :result="t.result"
        :elapsed="t.elapsed"
        :loading="t.result == null"
        :need-confirm="t.needConfirm"
        :start-time="t.startTime"
        :sub-steps="(t.subSteps as any)"
        @toolContent="(content: any) => $emit('toolContent', content)"
      />
    </TransitionGroup>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

/** 微果冻动画：ToolCallItem 出现/消失 */
.jelly-enter-active {
  animation: jelly-enter 0.35s cubic-bezier(0.34, 1.3, 0.64, 1) both;
}

.jelly-leave-active {
  animation: jelly-leave 0.2s ease-out both;
}

.jelly-move {
  transition: transform 0.3s ease;
}

@keyframes jelly-enter {
  0% {
    opacity: 0;
    transform: translateY(3px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes jelly-leave {
  0% {
    opacity: 1;
    transform: scale(0.99);
  }
  100% {
    opacity: 0;
    transform: scale(0.98);
  }
}
</style>

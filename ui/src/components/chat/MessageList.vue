<script setup lang="ts">
import MessageItem from './MessageItem.vue'
import ToolCallItem from './ToolCallItem.vue'
import type { DisplayMessage } from '@/types'
import type {FlatFileItem} from "@/composables/chat/useWorkspaceFiles.ts";
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'

defineProps<{
  messages: DisplayMessage[]
  agentHasResult?: boolean
  toolCalls: Array<{ id: string; name: string; args: string; result?: string; elapsed?: number, needConfirm?: boolean }>
}>()

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
    <MessageItem
      v-for="(msg, index) in messages"
      @inputTagPreview="$emit('inputTagPreview', $event as FlatFileItem)"
      @interaction-submit="$emit('interactionSubmit', $event)"
      @uip-retry="$emit('uipRetry', $event)"
      @vep-retry="$emit('vepRetry', $event)"
      :id="msg.id"
      :current-index="index"
      :total-messages="messages.length"
      :key="msg.id"
      :role="msg.role"
      :content="msg.content"
      :created-at="msg.createdAt"
      :agent-has-result="agentHasResult"
      :is-streaming="msg.isStreaming"
    />
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

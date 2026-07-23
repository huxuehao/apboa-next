<script setup lang="ts">
import ChatInput from './ChatInput.vue'
import type { CommonQuestion } from '@/types'
import { resolveCommonQuestionIcon } from '@/components/agent/commonQuestionIcons'

defineProps<{
  messageSize: number
  headline: string
  inputValue: string
  agentId: string
  description?: string
  /** 智能体自定义头像（base64 data URL），未设置不渲染 */
  agentAvatar?: string | null
  commonQuestions?: CommonQuestion[] | null
  uploadedFiles?: import('@/types').UploadedFileItem[]
  isRunning?: boolean
  planActive?: boolean
  enablePlanning?: boolean
  toolProcessActive?: boolean
  showToolProcess?: boolean
  confirmMode?: import('@/api/chatSession').ConfirmMode
  thinkingSupported?: boolean
  thinkingActive?: boolean
  /** 候选模型选项（>1 才展示切换按钮） */
  modelOptions?: import('@/types').AgentModelOptionVO[]
  /** 会话当前生效模型 id */
  activeModelId?: string
  /** 有待确认的 HITL 操作：禁用模型/思考切换 */
  hasPendingConfirm?: boolean
  allowUploadFileType?: string[]
  sessionId?: string | null
  mentionAllowed?: boolean
  voiceState?: import('@/composables/chat/useVoiceInput').VoiceInputState
}>()

defineEmits<{
  (e: 'update:inputValue', value: string): void
  (e: 'update:uploadedFiles', value: import('@/types').UploadedFileItem[]): void
  (e: 'send'): void
  (e: 'plan', value: boolean): void
  (e: 'toolProcess', value: boolean): void
  (e: 'confirmMode', value: import('@/api/chatSession').ConfirmMode): void
  (e: 'thinking', value: boolean): void
  (e: 'model', value: string): void
  (e: 'voiceToggle'): void
  (e: 'voicePress', action: import('@/composables/chat/useVoiceInput').VoicePressAction): void
  (e: 'newSession'): void
  (e: 'quickQuestion', question: string): void
}>()
</script>

<template>
  <div class="chat-welcome">
    <img v-if="agentAvatar" :src="agentAvatar" alt="头像" class="chat-welcome-avatar" />
    <h2 class="chat-welcome-title" :title="headline">{{ headline }}</h2>
    <p v-if="description" class="chat-welcome-desc" :title="description">{{ description }}</p>
    <div v-if="commonQuestions && commonQuestions.length > 0" class="chat-welcome-questions">
      <div class="chat-welcome-questions-label">试试这些常用问题</div>
      <div class="chat-welcome-questions-grid">
        <button
          v-for="(q, index) in commonQuestions"
          :key="index"
          type="button"
          class="chat-welcome-question-card"
          :title="q.question"
          @click="$emit('quickQuestion', q.question)"
        >
          <span
            v-if="resolveCommonQuestionIcon(q.icon)"
            class="question-card-icon"
            :style="{ color: q.color || 'var(--color-primary)' }"
          >
            <component :is="resolveCommonQuestionIcon(q.icon)" />
          </span>
          <span class="question-card-content">
            <span class="question-card-title">{{ q.title }}</span>
            <span class="question-card-question">{{ q.question }}</span>
          </span>
        </button>
      </div>
    </div>
    <div class="chat-input-outer chat-welcome-input">
      <ChatInput
        :model-value="inputValue"
        :agent-id="agentId"
        :uploaded-files="uploadedFiles"
        :isRunning="isRunning"
        :plan-active="planActive"
        :enable-planning="enablePlanning"
        :allow-upload-file-type="allowUploadFileType"
        :show-tool-process="showToolProcess"
        :tool-process-active="toolProcessActive"
        :confirm-mode="confirmMode"
        :thinking-supported="thinkingSupported"
        :thinking-active="thinkingActive"
        :model-options="modelOptions"
        :active-model-id="activeModelId"
        :has-pending-confirm="hasPendingConfirm"
        :voice-state="voiceState"
        :session-id="sessionId"
        :mention-allowed="mentionAllowed"
        @update:model-value="$emit('update:inputValue', $event)"
        @update:uploaded-files="$emit('update:uploadedFiles', $event)"
        @plan="$emit('plan', $event)"
        @toolProcess="$emit('toolProcess', $event)"
        @confirm-mode="$emit('confirmMode', $event)"
        @thinking="$emit('thinking', $event)"
        @model="$emit('model', $event)"
        @voice-toggle="$emit('voiceToggle')"
        @voice-press="$emit('voicePress', $event)"
        @send="$emit('send')"
        @new-session="$emit('newSession')"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;
</style>

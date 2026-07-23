<script setup lang="ts">
/**
 * 聊天输入框容器组件
 * 组合附件、编辑器、工具栏三大子组件，对外保留原 props/emits 契约
 *
 * @component
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { MessageOutlined } from '@ant-design/icons-vue'
import ChatInputAttachments from './ChatInputAttachments.vue'
import ChatInputEditor from './ChatInputEditor.vue'
import ChatInputToolbar from './ChatInputToolbar.vue'
import VoiceWaveBar from './VoiceWaveBar.vue'
import VoicePressBar from './VoicePressBar.vue'
import { useChatAttachments } from '@/composables/chat/useChatAttachments'
import { useChatStore } from '@/stores/modules/chat'
import { useAccountStore } from '@/stores/modules/account'
import type { FlatFileItem } from '@/composables/chat/useWorkspaceFiles'
import type { UploadedFileItem } from '@/types'

const props = withDefaults(
  defineProps<{
    modelValue: string
    agentId: string
    uploadedFiles?: UploadedFileItem[]
    isRunning?: boolean
    placeholder?: string
    memoryActive?: boolean
    planActive?: boolean
    enableMemory?: boolean
    enablePlanning?: boolean
    toolProcessActive?: boolean
    showToolProcess?: boolean
    confirmMode?: import('@/api/chatSession').ConfirmMode
    thinkingSupported?: boolean
    thinkingActive?: boolean
    allowUploadFileType?: string[]
    sessionId?: string | null
    mentionAllowed?: boolean
    needInit?: boolean
    voiceState?: import('@/composables/chat/useVoiceInput').VoiceInputState
  }>(),
  {
    uploadedFiles: () => [],
    memoryActive: false,
    planActive: false,
    enableMemory: false,
    enablePlanning: false,
    toolProcessActive: false,
    showToolProcess: false,
    confirmMode: 'MANUAL',
    sessionId: null,
    mentionAllowed: false,
    needInit: false
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:uploadedFiles', value: UploadedFileItem[]): void
  (e: 'send'): void
  (e: 'abort'): void
  (e: 'memory', value: boolean): void
  (e: 'plan', value: boolean): void
  (e: 'toolProcess', value: boolean): void
  (e: 'confirmMode', value: import('@/api/chatSession').ConfirmMode): void
  (e: 'thinking', value: boolean): void
  (e: 'voiceToggle'): void
  (e: 'voicePress', action: import('@/composables/chat/useVoiceInput').VoicePressAction): void
  (e: 'inputTagPreview', value: FlatFileItem): void
  (e: 'newSession'): void
}>()

const fileInputRef = ref<HTMLInputElement | null>()
const editorRef = ref<InstanceType<typeof ChatInputEditor> | null>()

/** 附件操作集合 */
const { fileAcceptAttr, handleFileChange, removeFile } = useChatAttachments({
  getFiles: () => props.uploadedFiles ?? [],
  setFiles: (files) => emit('update:uploadedFiles', files),
  getAllowedTypes: () => props.allowUploadFileType
})

/** 当前 input accept 属性值 */
const fileAccept = computed(() => fileAcceptAttr())

/**
 * 综合判断是否允许触发发送：
 * - 没有上传中的文件
 * - 文本内容或非上传中附件至少存在其一
 */
const canSend = computed(() => {
  const files = props.uploadedFiles ?? []
  const hasUploading = files.some((f) => f.uploading)
  if (hasUploading) return false
  const hasText = props.modelValue.trim().length > 0
  const hasReadyAttach = files.filter((f) => !f.uploading).length > 0
  return hasText || hasReadyAttach
})

/** 输入区是否有内容（文字或附件，含上传中）：决定主按钮显示麦克风还是发送 */
const hasContent = computed(() => {
  return props.modelValue.trim().length > 0 || (props.uploadedFiles?.length ?? 0) > 0
})

/* ============================================================
 * 移动端输入模式（键盘/按住说话）
 * ============================================================ */
const chatStore = useChatStore()
const accountStore = useAccountStore()

const isMobile = ref(window.innerWidth <= 768)
const onViewportResize = () => {
  isMobile.value = window.innerWidth <= 768
}
onMounted(() => window.addEventListener('resize', onViewportResize))
onBeforeUnmount(() => window.removeEventListener('resize', onViewportResize))

/** 输入模式偏好（per-agent 持久化，防止欢迎态→对话态组件重建后模式回跳）；移动端默认语音 */
const inputMode = computed(() =>
  chatStore.getVoiceInputMode(
    props.agentId,
    accountStore.userInfo?.id as string | undefined,
    isMobile.value ? 'voice' : 'keyboard'
  )
)

/** 是否处于「按住说话」形态（仅移动端 + 已绑 ASR + 语音模式） */
const voicePressMode = computed(
  () => isMobile.value && !!props.voiceState?.enabled && inputMode.value === 'voice'
)

/**
 * 语音模式下出现内容（上传附件）自动切回键盘：主按钮随即变为发送、可直接发出，
 * 与「有内容↔键盘模式」的门槛规则自洽（进语音的门槛即无内容）。
 * 按住说话的「回填→发送→清空」在同一 tick 内完成，watch 批处理看不到中间态，不会误触发
 */
watch(hasContent, (has) => {
  if (has && voicePressMode.value) {
    chatStore.setVoiceInputMode(props.agentId, accountStore.userInfo?.id as string | undefined, 'keyboard')
  }
})

/**
 * 切换输入模式；切回键盘时自动聚焦编辑器调起软键盘。
 * 已知瑕疵（暂搁置）：iOS 程序化聚焦后输入框位置偏高，preventScroll 与
 * 去聚焦两方案实测均未改善观感，维持原始行为待后续专项处理
 */
const handleSwitchInputMode = () => {
  const next = inputMode.value === 'voice' ? 'keyboard' : 'voice'
  chatStore.setVoiceInputMode(props.agentId, accountStore.userInfo?.id as string | undefined, next)
  if (next === 'keyboard') {
    nextTick(() => editorRef.value?.focus())
  }
}

/** 录音计时（m:ss），显示在录音条上 */
const voiceRecordingTime = computed(() => {
  const total = props.voiceState?.seconds ?? 0
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})

/**
 * v-model 文本透传
 */
const handleEditorUpdate = (value: string) => {
  emit('update:modelValue', value)
}

/**
 * 首帧动画闸门：避免初次挂载时从展开默认值补间到折叠态导致的卡顿。
 * 双 rAF 等浏览器完成首帧布局后再开启过渡，首屏以静态姿态呈现。
 */
const animationsReady = ref(false)
onMounted(() => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      animationsReady.value = true
    })
  })
})
</script>

<template>
  <!--
    外层 stage：flex 居中容器，确保 shell 的宽度变化由中轴向两侧同步扩收。
    单容器形变方案：是同一个 div 在 needInit 变化时同步调整
    宽度/高度/圆角/内边距，达成连贯的"凝缩为按钮"动画。
    内部两层内容（welcome-face / input-body）仅负责透明度交叠。
  -->
  <div class="chat-input-stage" :class="{ 'is-ready': animationsReady }">
    <div
      class="chat-input-shell"
      :class="{ 'is-collapsed': needInit }"
      role="presentation"
      @click="needInit ? emit('newSession') : undefined"
    >
    <!-- 折叠态贴面：绝对定位，随容器同步变形 -->
    <div class="chat-welcome-face" :aria-hidden="!needInit">
      <span class="chat-welcome-shine" aria-hidden="true"></span>
      <MessageOutlined class="chat-welcome-icon" />
      <span class="chat-welcome-label">开始对话</span>
    </div>

    <!-- 展开态内容：高度随容器裁剪，透明度与轻位移着色 -->
    <div class="chat-input-body" :inert="needInit">
      <input
        ref="fileInputRef"
        type="file"
        class="chat-file-input-hidden"
        :accept="fileAccept"
        multiple
        @change="handleFileChange"
      />

      <ChatInputAttachments
        :files="uploadedFiles ?? []"
        @remove="removeFile"
      />

      <div
        v-if="voiceState?.status === 'recording' && voiceState?.mode !== 'press'"
        class="chat-voice-recording"
      >
        <span class="chat-voice-recording-time">{{ voiceRecordingTime }}</span>
        <VoiceWaveBar :level="voiceState?.level ?? 0" :count="isMobile ? 18 : 44" />
        <span class="chat-voice-recording-hint">
          {{ voiceState?.mode === 'ptt' ? '松开空格发送 · Esc 取消' : '点击右下角按钮结束 · Esc 取消' }}
        </span>
      </div>
      <VoicePressBar
        v-else-if="voicePressMode"
        :voice-state="voiceState"
        :disabled="isRunning"
        @press="(action) => emit('voicePress', action)"
      />
      <ChatInputEditor
        v-else
        ref="editorRef"
        :agent-id="agentId"
        :account-id="accountStore.userInfo?.id"
        :model-value="modelValue"
        :placeholder="placeholder || (voiceState?.enabled ? (isMobile ? '发消息...' : '发消息或按住空格说话...') : '输入消息...')"
        :session-id="sessionId"
        :mention-allowed="mentionAllowed"
        :is-running="isRunning"
        @update:model-value="handleEditorUpdate"
        @send="emit('send')"
        @input-tag-preview="(item) => emit('inputTagPreview', item)"
      />

      <ChatInputToolbar
        :is-running="isRunning"
        :can-send="canSend"
        :enable-memory="enableMemory"
        :memory-active="memoryActive"
        :show-tool-process="showToolProcess"
        :tool-process-active="toolProcessActive"
        :confirm-mode="confirmMode"
        :thinking-supported="thinkingSupported"
        :thinking-active="thinkingActive"
        :mention-allowed="mentionAllowed"
        :allow-upload-file-type="allowUploadFileType"
        :voice-state="voiceState"
        :has-content="hasContent"
        :input-mode="inputMode"
        :mobile-voice-ui="isMobile && !!voiceState?.enabled"
        :is-mobile="isMobile"
        @switch-input-mode="handleSwitchInputMode"
        @memory="(v) => emit('memory', v)"
        @tool-process="(v) => emit('toolProcess', v)"
        @confirm-mode="(v: import('@/api/chatSession').ConfirmMode) => emit('confirmMode', v)"
        @thinking="(v: boolean) => emit('thinking', v)"
        @mention-trigger="editorRef?.triggerMention()"
        @pick-file="fileInputRef?.click()"
        @voice-toggle="emit('voiceToggle')"
        @send="emit('send')"
        @abort="emit('abort')"
      />
    </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

/**
 * 外层 stage：始终 100% 宽，用 flex 居中包裹 shell。
 * 这样 shell.width 从 150px 补间到 100% 时，始终被锁在中轴，
 * 呈现"从中央同步向两侧扩张"的观感，避免 margin:auto 不能补间导致的单向右展开。
 */
.chat-input-stage {
  display: flex;
  justify-content: center;
  width: 100%;
}

/**
 * 首屏闸门：只有 .is-ready 挂上后才启用过渡，避免初次挂载从默认值补间到折叠态。
 */
.chat-input-stage.is-ready .chat-input-shell {
  transition:
    width 0.55s cubic-bezier(0.22, 1, 0.36, 1),
    max-width 0.55s cubic-bezier(0.22, 1, 0.36, 1),
    max-height 0.55s cubic-bezier(0.22, 1, 0.36, 1),
    padding 0.45s cubic-bezier(0.22, 1, 0.36, 1),
    border-radius 0.45s cubic-bezier(0.22, 1, 0.36, 1),
    background-color 0.32s ease,
    border-color 0.32s ease,
    box-shadow 0.4s cubic-bezier(0.22, 1, 0.36, 1),
    color 0.32s ease;
}

/**
 * 外壳：展开态与折叠态共享同一个容器。
 * 过渡过程同步动画 width / max-width / max-height / padding / border-radius，
 * 并使用略微差别的时长与缓动曲线营造有机的"凝结感"。
 */
.chat-input-shell {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  width: 100%;
  max-width: 100%;
  max-height: 400px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-light);
  border-radius: 24px;
  background-color: $chat-bg-main;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  // 展开态不裁剪，避免 ResourceMentionDropdown 等绝对定位弹层被 clip
  overflow: visible;
  transform-origin: center;
  will-change: width, max-width, max-height, padding, border-radius;

  &:focus-within {
    border-color: $chat-primary;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06), 0 0 0 2px rgba($chat-primary, 0.1);
  }

  // 折叠态：同一容器收敛为按钮形态
  &.is-collapsed {
    width: 150px;
    max-width: 150px;
    max-height: 40px;
    padding: 10px 15px;
    border-radius: 8px;
    gap: 0;
    cursor: pointer;
    user-select: none;
    color: #5c5c5c;
    font-weight: 500;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    // 折叠时才启用裁剪，以配合 max-height 收缩 + 隐藏 body 内容
    overflow: hidden;

    &:hover {
      background-color: #f2f4f7;
      color: #232323;
    }

    &:active {
      transform: translateY(0) scale(0.98);
      box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
      transition-duration: 0.12s;
    }
  }
}

/**
 * 折叠态文案层：与 shell 同步变形（border-radius: inherit）。
 * 默认透明；折叠态下以轻微延迟渐亮，营造"容器先收缩、文案后凝结"的质感。
 */
.chat-welcome-face {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  letter-spacing: 0.2px;
  border-radius: inherit;
  color: inherit;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: 1;
}

.chat-input-stage.is-ready .chat-welcome-face {
  transition:
    opacity 0.22s ease 0s,
    border-radius 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}

.chat-input-shell.is-collapsed .chat-welcome-face {
  opacity: 1;
}

.chat-input-stage.is-ready .chat-input-shell.is-collapsed .chat-welcome-face {
  // 等容器收缩接近到位后再呈现文案
  transition:
    opacity 0.32s ease 0.2s,
    border-radius 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}

.chat-welcome-icon {
  font-size: 15px;
}

.chat-input-stage.is-ready .chat-welcome-icon {
  transition: transform 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}

.chat-welcome-label {
  line-height: 1;
}

/** 折叠态悬停时的微光扫过 */
.chat-welcome-shine {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  background: linear-gradient(
    120deg,
    transparent 20%,
    rgba(255, 255, 255, 0.55) 50%,
    transparent 80%
  );
  transform: translateX(-120%);
  opacity: 0;
}

.chat-input-stage.is-ready .chat-welcome-shine {
  transition:
    transform 0.7s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.4s ease;
}

/**
 * 展开态内容层：高度随 shell 裁剪，同时用 opacity / translateY 增强进场质感。
 * 折叠时快速淡出为 shell 收缩让位；展开时略微延迟出现，让容器先伸展到位。
 */
.chat-input-body {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  opacity: 1;
  transform: translateY(0);
  z-index: 2;
}

.chat-input-stage.is-ready .chat-input-body {
  transition:
    opacity 0.32s ease 0.18s,
    transform 0.4s cubic-bezier(0.22, 1, 0.36, 1) 0.18s;
}

.chat-input-shell.is-collapsed .chat-input-body {
  opacity: 0;
  transform: translateY(4px);
  pointer-events: none;
}

.chat-input-stage.is-ready .chat-input-shell.is-collapsed .chat-input-body {
  // 折叠时并不延迟，让容器裁剪与文案出现形成衰减叠加
  transition:
    opacity 0.18s ease 0s,
    transform 0.32s cubic-bezier(0.22, 1, 0.36, 1) 0s;
}

.chat-file-input-hidden {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}

/* 录音条：录音期间替换编辑器区域，「计时·波浪·提示」紧邻成组、整组居中 */
.chat-voice-recording {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 60px;
  padding: 5px 0;
}

.chat-voice-recording-time {
  font-size: 13px;
  color: #ff4d4f;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.chat-voice-recording-hint {
  font-size: 12px;
  color: var(--color-text-placeholder);
  flex-shrink: 0;
}

// 尊重用户的减弱动效偏好
@media (prefers-reduced-motion: reduce) {
  .chat-input-shell,
  .chat-welcome-face,
  .chat-welcome-icon,
  .chat-welcome-shine,
  .chat-input-body {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}
</style>

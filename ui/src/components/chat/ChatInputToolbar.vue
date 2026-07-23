<script setup lang="ts">
/**
 * 聊天输入框工具栏组件
 * 负责记忆开关、工具调用历史开关、授权模式开关、@按钮、上传按钮、发送/中断按钮
 *
 * @component
 */
import { computed } from 'vue'
import {
  ArrowUpOutlined,
  AudioOutlined,
  BulbOutlined,
  ClockCircleOutlined,
  LoadingOutlined,
  LockOutlined,
  MessageOutlined,
  PaperClipOutlined,
  StopOutlined,
  ThunderboltOutlined,
  UnlockOutlined
} from '@ant-design/icons-vue'
import type { ConfirmMode } from '@/api/chatSession'

const props = withDefaults(
  defineProps<{
    isRunning?: boolean
    /** 是否允许触发发送（综合上传中、内容、附件等条件） */
    canSend: boolean
    enableMemory?: boolean
    memoryActive?: boolean
    showToolProcess?: boolean
    toolProcessActive?: boolean
    /** HITL 授权模式（三态） */
    confirmMode?: ConfirmMode
    /** 模型是否支持会话级思考开关（DASH_SCOPE，驱动按钮显隐） */
    thinkingSupported?: boolean
    /** 会话思考模式有效值 */
    thinkingActive?: boolean
    mentionAllowed?: boolean
    allowUploadFileType?: string[]
    /** 语音输入聚合状态（未透传视为不可用） */
    voiceState?: import('@/composables/chat/useVoiceInput').VoiceInputState
    /** 输入区是否有内容（文字或附件）：决定主按钮显示麦克风还是发送 */
    hasContent?: boolean
    /** 移动端输入模式（voice=按住说话形态） */
    inputMode?: 'keyboard' | 'voice'
    /** 移动端语音 UI 生效（主按钮承担模式切换语义：麦克风=进语音，键盘=回打字） */
    mobileVoiceUi?: boolean
    /** 移动端标记：触屏 tap 触发的伪 hover 会让 Tooltip 粘滞不消失，移动端禁用 */
    isMobile?: boolean
  }>(),
  {
    isRunning: false,
    enableMemory: false,
    memoryActive: false,
    showToolProcess: false,
    toolProcessActive: false,
    confirmMode: 'MANUAL',
    thinkingSupported: false,
    thinkingActive: true,
    mentionAllowed: false,
    hasContent: false,
    inputMode: 'keyboard',
    mobileVoiceUi: false,
    isMobile: false
  }
)

/** Tooltip 触发方式：移动端禁用（触屏无悬停语义，tap 只会留下粘滞的提示框） */
const tooltipTrigger = computed<Array<'hover'>>(() => (props.isMobile ? [] : ['hover']))

const emit = defineEmits<{
  (e: 'memory', value: boolean): void
  (e: 'toolProcess', value: boolean): void
  (e: 'confirmMode', value: ConfirmMode): void
  (e: 'thinking', value: boolean): void
  (e: 'mentionTrigger'): void
  (e: 'pickFile'): void
  (e: 'voiceToggle'): void
  (e: 'switchInputMode'): void
  (e: 'send'): void
  (e: 'abort'): void
}>()

/**
 * 主按钮合一：黄金位永远显示当前最有意义的动作。
 * 优先级：AI 回复中(停止) > 桌面录音中(红色结束) > 识别中(loading)
 * > 移动端语音模式(键盘图标=切回打字) > 空输入(麦克风) > 发送。
 * 移动端麦克风的点击语义是「切换到按住说话」，桌面是「开始录音」。
 */
const inVoiceMode = computed(() => !!props.mobileVoiceUi && props.inputMode === 'voice')

/**
 * 文本编辑器是否在场：@ 按钮是往编辑器光标处插入标签的，
 * 语音模式（按住说话条）与录音中（录音条）编辑器均未渲染，@ 无意义则隐藏
 */
const editorPresent = computed(
  () => props.voiceState?.status !== 'recording' && !inVoiceMode.value
)

const showMicButton = computed(() =>
  !!props.voiceState?.enabled &&
  !props.hasContent &&
  props.voiceState?.status === 'idle' &&
  !props.isRunning &&
  // 仅移动端语音 UI 下 voice 模式才收敛语音入口到按住说话条；
  // 桌面端不受 store 里残留的移动端模式偏好影响（偏好按 agent 存、不分端）
  (!props.mobileVoiceUi || props.inputMode !== 'voice')
)

const primaryDisabled = computed(() => {
  if (props.isRunning) return false
  const status = props.voiceState?.status
  if (status === 'transcribing') return true
  // 按住说话录音中禁点主按钮（手指在按住说话条上，交互收敛在条上）
  if (status === 'recording') return props.voiceState?.mode === 'press'
  if (inVoiceMode.value) return false
  if (showMicButton.value) return false
  return !props.canSend
})

const primaryTooltip = computed(() => {
  if (props.isRunning) return ''
  const status = props.voiceState?.status
  if (status === 'recording') {
    if (props.voiceState?.mode === 'press') return ''
    return props.voiceState?.mode === 'ptt' ? '松开空格发送（Esc 取消）' : '点击结束并识别（Esc 取消）'
  }
  if (status === 'transcribing') return '识别中…'
  if (inVoiceMode.value) return '切换为键盘输入'
  if (showMicButton.value) return props.mobileVoiceUi ? '切换为按住说话' : '点击说话'
  return ''
})

const handlePrimaryClick = () => {
  if (props.isRunning) {
    emit('abort')
    return
  }
  const status = props.voiceState?.status
  if (status === 'recording') {
    if (props.voiceState?.mode !== 'press') {
      emit('voiceToggle')
    }
    return
  }
  if (status === 'transcribing') {
    return
  }
  if (inVoiceMode.value) {
    emit('switchInputMode')
    return
  }
  if (showMicButton.value) {
    if (props.mobileVoiceUi) {
      emit('switchInputMode')
    } else {
      emit('voiceToggle')
    }
    return
  }
  emit('send')
}

/** 记忆开关菜单项（菜单自解释：标题+行为说明，两端统一下拉、不依赖 hover tooltip） */
const MEMORY_OPTIONS = [
  { key: 'on', label: '开启记忆', desc: '对话携带上下文历史，记住之前聊过的内容' },
  { key: 'off', label: '关闭记忆', desc: '每条消息独立发送，不携带历史' }
] as const

/** 记忆按钮 tooltip（桌面悬停简述；移动端已禁用 tooltip，信息由菜单承载） */
const memoryTip = computed(() => {
  if (!props.enableMemory) return '该智能体不支持记忆持久化'
  return props.memoryActive ? '记忆已开启（点击选择）' : '记忆已关闭（点击选择）'
})

const handleMemorySelect = ({ key }: { key: string | number }) => {
  const next = key === 'on'
  if (next !== props.memoryActive) {
    emit('memory', next)
  }
}

/**
 * 切换工具调用历史按钮，未启用时不响应
 */
const toggleToolProcess = () => {
  if (!props.showToolProcess) return
  emit('toolProcess', !props.toolProcessActive)
}

/** 授权模式选项（下拉菜单项：标题 + 行为说明），选择即向上冒泡由上层写 Redis 并联动存量 pending */
const CONFIRM_MODE_OPTIONS: Array<{ key: ConfirmMode; label: string; desc: string }> = [
  { key: 'AUTO_APPROVE', label: '一键授权', desc: '需确认的工具自动允许执行' },
  { key: 'MANUAL', label: '逐步确认', desc: '每个需确认的工具等待手动决策' },
  { key: 'AUTO_REJECT', label: '拒绝授权', desc: '需确认的工具自动拒绝执行' }
]

/** 触发按钮的 tooltip：当前模式名 + 行为说明 */
const confirmModeTip = computed(() => {
  const opt = CONFIRM_MODE_OPTIONS.find((o) => o.key === props.confirmMode)
  return opt ? `${opt.label}：${opt.desc}（点击切换）` : ''
})

const handleConfirmModeSelect = ({ key }: { key: string | number }) => {
  const mode = String(key) as ConfirmMode
  if (mode !== props.confirmMode) {
    emit('confirmMode', mode)
  }
}

/** 思考模式菜单项（仅支持开关的模型展示本按钮；下一条消息生效） */
const THINKING_OPTIONS = [
  { key: 'on', label: '开启思考', desc: '回答前先深度推理，更准确但更慢' },
  { key: 'off', label: '关闭思考', desc: '跳过深度推理直接回答，响应更快' }
] as const

const thinkingTip = computed(() =>
  props.thinkingActive ? '思考模式已开启（点击选择）' : '思考模式已关闭（点击选择）'
)

const handleThinkingSelect = ({ key }: { key: string | number }) => {
  const next = key === 'on'
  if (next !== props.thinkingActive) {
    emit('thinking', next)
  }
}

/** 多模态类型扩展名集合（用于 tooltip 分类） */
const IMAGE_EXTS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'ico'])
const AUDIO_EXTS = new Set(['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac', 'wma', 'mpeg'])
const VIDEO_EXTS = new Set(['mp4', 'webm', 'mov', 'mkv', 'avi', 'flv', 'm3u8', 'mpeg'])
const DOC_EXTS = new Set([
  'doc', 'docx', 'pdf', 'txt', 'md',
  'xlsx', 'xls', 'csv', 'pptx', 'ppt'
])

/** 上传按钮 tooltip：按类别聚合展示，避免扩展名列表过长 */
const uploadTooltip = computed(() => {
  const types = props.allowUploadFileType
  if (!types?.length) return '点击上传文件'

  const parts: string[] = []
  if (types.some((t) => IMAGE_EXTS.has(t))) parts.push('图片')
  if (types.some((t) => AUDIO_EXTS.has(t))) parts.push('音频')
  if (types.some((t) => VIDEO_EXTS.has(t))) parts.push('视频')
  if (types.some((t) => DOC_EXTS.has(t))) parts.push('文档')

  return parts.length > 0 ? `点击上传文件（${parts.join('、')}）` : '点击上传文件'
})
</script>

<template>
  <div class="chat-input-toolbar">
    <div class="chat-input-toolbar-left">
      <ADropdown :trigger="['click']" placement="topLeft" :disabled="!enableMemory">
        <ATooltip placement="bottom" :trigger="tooltipTrigger" :title="memoryTip">
          <button
            :disabled="!enableMemory"
            type="button"
            class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
            :class="{ 'is-active': memoryActive && enableMemory }"
          >
            <ClockCircleOutlined />
          </button>
        </ATooltip>
        <template #overlay>
          <AMenu
            class="toolbar-toggle-menu"
            :selected-keys="[memoryActive ? 'on' : 'off']"
            @click="handleMemorySelect"
          >
            <AMenuItem v-for="opt in MEMORY_OPTIONS" :key="opt.key">
              <div class="toolbar-toggle-option">
                <span class="toolbar-toggle-option-icon" :class="'is-' + opt.key">
                  <ClockCircleOutlined />
                </span>
                <span class="toolbar-toggle-option-text">
                  <span class="toolbar-toggle-option-label">{{ opt.label }}</span>
                  <span class="toolbar-toggle-option-desc">{{ opt.desc }}</span>
                </span>
              </div>
            </AMenuItem>
          </AMenu>
        </template>
      </ADropdown>

      <!-- 工具调用历史开关：暂不对外展示（仅隐藏 UI，props/事件链与后端逻辑保留，放开取消注释即可）
      <ATooltip placement="bottom" :trigger="tooltipTrigger">
        <template #title>
          <span v-if="showToolProcess">{{ (toolProcessActive && showToolProcess) ? '点击关闭工具调用历史' : '点击显示工具调用历史' }}</span>
          <span v-else>不支持控制工具调用显示</span>
        </template>
        <button
          :disabled="!showToolProcess"
          type="button"
          class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
          :class="{ 'is-active': toolProcessActive && showToolProcess }"
          @click="toggleToolProcess"
        >
          <ThunderboltOutlined />
        </button>
      </ATooltip>
      -->

      <ADropdown v-if="thinkingSupported" :trigger="['click']" placement="topLeft">
        <ATooltip placement="bottom" :trigger="tooltipTrigger" :title="thinkingTip">
          <button
            type="button"
            class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
            :class="{ 'is-active': thinkingActive }"
          >
            <BulbOutlined />
          </button>
        </ATooltip>
        <template #overlay>
          <AMenu
            class="toolbar-toggle-menu"
            :selected-keys="[thinkingActive ? 'on' : 'off']"
            @click="handleThinkingSelect"
          >
            <AMenuItem v-for="opt in THINKING_OPTIONS" :key="opt.key">
              <div class="toolbar-toggle-option">
                <span class="toolbar-toggle-option-icon" :class="'is-' + opt.key">
                  <BulbOutlined />
                </span>
                <span class="toolbar-toggle-option-text">
                  <span class="toolbar-toggle-option-label">{{ opt.label }}</span>
                  <span class="toolbar-toggle-option-desc">{{ opt.desc }}</span>
                </span>
              </div>
            </AMenuItem>
          </AMenu>
        </template>
      </ADropdown>

      <ADropdown :trigger="['click']" placement="topLeft">
        <ATooltip placement="bottom" :trigger="tooltipTrigger" :overlay-style="{ maxWidth: 'none' }" :overlay-inner-style="{ whiteSpace: 'nowrap' }" :title="confirmModeTip">
          <button
            type="button"
            class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
            :class="{
              'is-warn-active': confirmMode === 'AUTO_APPROVE',
              'is-active': confirmMode === 'MANUAL',
              'is-danger-active': confirmMode === 'AUTO_REJECT'
            }"
          >
            <UnlockOutlined v-if="confirmMode === 'AUTO_APPROVE'" />
            <StopOutlined v-else-if="confirmMode === 'AUTO_REJECT'" />
            <LockOutlined v-else />
          </button>
        </ATooltip>
        <template #overlay>
          <AMenu class="confirm-mode-menu" :selected-keys="[confirmMode]" @click="handleConfirmModeSelect">
            <AMenuItem v-for="opt in CONFIRM_MODE_OPTIONS" :key="opt.key">
              <div class="confirm-mode-option">
                <span class="confirm-mode-option-icon" :class="'is-' + opt.key.toLowerCase()">
                  <UnlockOutlined v-if="opt.key === 'AUTO_APPROVE'" />
                  <StopOutlined v-else-if="opt.key === 'AUTO_REJECT'" />
                  <LockOutlined v-else />
                </span>
                <span class="confirm-mode-option-text">
                  <span class="confirm-mode-option-label">{{ opt.label }}</span>
                  <span class="confirm-mode-option-desc">{{ opt.desc }}</span>
                </span>
              </div>
            </AMenuItem>
          </AMenu>
        </template>
      </ADropdown>
    </div>
    <div class="chat-input-toolbar-right">
      <!-- @ 添加上下文按钮（仅编辑器在场时显示） -->
      <ATooltip v-if="editorPresent" placement="bottom" :trigger="tooltipTrigger" title="添加上下文">
        <button
          :disabled="!mentionAllowed"
          type="button"
          class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle chat-toolbar-btn-accent"
          @mousedown.prevent
          @click="emit('mentionTrigger')"
        >
          @
        </button>
      </ATooltip>
      <ATooltip placement="bottom" :trigger="tooltipTrigger" :overlay-style="{ maxWidth: 'none' }" :overlay-inner-style="{ whiteSpace: 'nowrap' }" :title="uploadTooltip">
        <button
          type="button"
          class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle chat-toolbar-btn-accent"
          style="margin-right: 15px"
          @click="emit('pickFile')"
        >
          <PaperClipOutlined />
        </button>
      </ATooltip>
      <ATooltip placement="bottom" :trigger="tooltipTrigger" :title="primaryTooltip">
        <button
          type="button"
          class="chat-send-btn-inner"
          :class="{ 'voice-recording-btn': voiceState?.status === 'recording' && voiceState?.mode !== 'press' }"
          :disabled="primaryDisabled"
          @click="handlePrimaryClick"
        >
          <template v-if="isRunning"><div class="send"></div></template>
          <LoadingOutlined v-else-if="voiceState?.status === 'transcribing'" spin />
          <AudioOutlined v-else-if="voiceState?.status === 'recording' && voiceState?.mode !== 'press'" />
          <MessageOutlined v-else-if="inVoiceMode" />
          <AudioOutlined v-else-if="showMicButton" />
          <ArrowUpOutlined v-else />
        </button>
      </ATooltip>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

.chat-input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  min-height: 36px;
}

.chat-input-toolbar-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chat-input-toolbar-right {
  display: flex;
  align-items: center;
}

/* 主按钮录音态：红色呼吸，点击结束录音 */
.chat-send-btn-inner.voice-recording-btn {
  background-color: #ff4d4f !important;
  color: #fff !important;
  animation: voice-pulse 1.2s ease-in-out infinite;
}

@keyframes voice-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(255, 77, 79, 0.35);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(255, 77, 79, 0);
  }
}

.chat-toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background-color: #f5f5f5;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: color 0.2s ease, background-color 0.2s ease;
  border-radius: var(--border-radius-md);
  margin-right: 10px;

  /* hover 仅在有真实悬停能力的设备生效：触屏 tap 会触发粘滞 hover，
     灰底会盖住 is-active 激活色，造成「点了颜色只变一点点」的错觉 */
  @media (hover: hover) {
    &:hover {
      color: $chat-primary;
      background-color: rgba($chat-primary, 0.06);
    }
  }

  &.is-active {
    color: $chat-primary;
    background-color: rgba($chat-primary, 0.1);
    font-weight: 500;
  }

  /* 一键授权激活态：警示色，提示需确认工具将自动放行 */
  &.is-warn-active {
    color: #fa8c16;
    background-color: rgba(250, 140, 22, 0.12);
    font-weight: 500;

    @media (hover: hover) {
      &:hover {
        color: #fa8c16;
        background-color: rgba(250, 140, 22, 0.18);
      }
    }
  }

  /* 拒绝授权激活态：危险色，提示需确认工具将被自动拒绝 */
  &.is-danger-active {
    color: #f5222d;
    background-color: rgba(245, 34, 45, 0.1);
    font-weight: 500;

    @media (hover: hover) {
      &:hover {
        color: #f5222d;
        background-color: rgba(245, 34, 45, 0.16);
      }
    }
  }

  &:disabled,
  &[disabled] {
    &:hover {
      cursor: not-allowed;
      color: var(--color-text-secondary);
      background-color: transparent;
    }
  }
}

.chat-toolbar-btn-text {
  padding: 6px 10px;
  font-size: var(--font-size-sm);
}

.chat-toolbar-btn-icon {
  width: 32px;
  height: 32px;
  font-size: 16px;
}

.chat-toolbar-btn-circle {
  border-radius: 50%;
}

/* 强调色图标按钮（@ / 附件）：图标品牌蓝、圆底保持浅灰；禁用回退灰色 */
.chat-toolbar-btn-accent {
  color: $chat-primary;

  &:disabled,
  &[disabled] {
    color: var(--color-text-secondary);
  }
}

.chat-send-btn-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: $chat-primary;
  border: none;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;

  @media (hover: hover) {
    &:hover:not(:disabled) {
      transform: scale(1.05);
    }
  }

  &:disabled {
    background-color: #e0e0e0;
    cursor: not-allowed;
    opacity: 0.6;
  }

  .send {
    width: 13px;
    height: 13px;
    background-color: #fff;
    border-radius: 2px;
  }
}
</style>

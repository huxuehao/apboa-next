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
  BulbOutlined,
  ClockCircleOutlined,
  LockOutlined,
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
    mentionAllowed: false
  }
)

const emit = defineEmits<{
  (e: 'memory', value: boolean): void
  (e: 'toolProcess', value: boolean): void
  (e: 'confirmMode', value: ConfirmMode): void
  (e: 'thinking', value: boolean): void
  (e: 'mentionTrigger'): void
  (e: 'pickFile'): void
  (e: 'send'): void
  (e: 'abort'): void
}>()

/**
 * 切换记忆按钮，未启用记忆能力时不响应
 */
const toggleMemory = () => {
  if (!props.enableMemory) return
  emit('memory', !props.memoryActive)
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

/** 切换会话思考模式（仅支持开关的模型展示本按钮；下一条消息生效） */
const toggleThinking = () => {
  emit('thinking', !props.thinkingActive)
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
      <ATooltip placement="bottom">
        <template #title>
          <span v-if="enableMemory">{{ (memoryActive && enableMemory) ? '点击关闭记忆' : '点击开启记忆' }}</span>
          <span v-else>不支持记忆持久化</span>
        </template>
        <button
          :disabled="!enableMemory"
          type="button"
          class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
          :class="{ 'is-active': memoryActive && enableMemory }"
          @click="toggleMemory"
        >
          <ClockCircleOutlined />
        </button>
      </ATooltip>

      <!-- 工具调用历史开关：暂不对外展示（仅隐藏 UI，props/事件链与后端逻辑保留，放开取消注释即可）
      <ATooltip placement="bottom">
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

      <ATooltip v-if="thinkingSupported" placement="bottom" :overlay-style="{ maxWidth: 'none' }" :overlay-inner-style="{ whiteSpace: 'nowrap' }">
        <template #title>
          {{ thinkingActive ? '点击关闭思考模式' : '点击开启思考模式' }}
        </template>
        <button
          type="button"
          class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
          :class="{ 'is-active': thinkingActive }"
          @click="toggleThinking"
        >
          <BulbOutlined />
        </button>
      </ATooltip>

      <ADropdown :trigger="['click']" placement="topLeft">
        <ATooltip placement="bottom" :overlay-style="{ maxWidth: 'none' }" :overlay-inner-style="{ whiteSpace: 'nowrap' }" :title="confirmModeTip">
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
      <!-- @ 添加上下文按钮 -->
      <ATooltip placement="bottom" title="添加上下文">
        <button
          :disabled="!mentionAllowed"
          type="button"
          class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
          @mousedown.prevent
          @click="emit('mentionTrigger')"
        >
          @
        </button>
      </ATooltip>
      <ATooltip placement="bottom" :overlay-style="{ maxWidth: 'none' }" :overlay-inner-style="{ whiteSpace: 'nowrap' }" :title="uploadTooltip">
        <button
          type="button"
          class="chat-toolbar-btn chat-toolbar-btn-icon chat-toolbar-btn-circle"
          style="margin-right: 15px"
          @click="emit('pickFile')"
        >
          <PaperClipOutlined />
        </button>
      </ATooltip>
      <button
        type="button"
        class="chat-send-btn-inner"
        :disabled="!isRunning && !canSend"
        @click="isRunning ? emit('abort') : emit('send')"
      >
        <template v-if="isRunning"><div class="send"></div></template>
        <ArrowUpOutlined v-else />
      </button>
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

  &:hover {
    color: $chat-primary;
    background-color: rgba($chat-primary, 0.06);
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

    &:hover {
      color: #fa8c16;
      background-color: rgba(250, 140, 22, 0.18);
    }
  }

  /* 拒绝授权激活态：危险色，提示需确认工具将被自动拒绝 */
  &.is-danger-active {
    color: #f5222d;
    background-color: rgba(245, 34, 45, 0.1);
    font-weight: 500;

    &:hover {
      color: #f5222d;
      background-color: rgba(245, 34, 45, 0.16);
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

  &:hover:not(:disabled) {
    transform: scale(1.05);
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

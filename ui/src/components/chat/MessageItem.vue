<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { LoadingOutlined, BulbOutlined, CopyOutlined, CheckOutlined, ToolOutlined, RightOutlined, DownOutlined } from '@ant-design/icons-vue'
import MediaPreview from '@/components/common/MediaPreview.vue'
import type { UploadedFileItem } from '@/types'
import MediaIcon from '@/components/common/MediaIcon.vue'
import MarkdownRenderer from "@/components/markdown/MarkdownRenderer.vue";
import TaggedContentRenderer from './TaggedContentRenderer.vue';
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'
import { formatElapsed } from '@/utils/chat/format'

const FILE_SEP = '@==##::::##==@'

/**
 * 解析用户内容，分离文件和文本
 */
function parseUserContent(content: string): { files: UploadedFileItem[]; text: string } {
  const idx = content.indexOf(FILE_SEP)
  if (idx === -1) return { files: [], text: content }
  const prefix = content.slice(0, idx)
  const text = content.slice(idx + FILE_SEP.length)
  try {
    const parsed = JSON.parse(prefix) as { files?: UploadedFileItem[] }
    const files = Array.isArray(parsed?.files) ? parsed.files : []
    return { files, text }
  } catch {
    return { files: [], text: content }
  }
}

/** 从文件名解析扩展名（小写） */
const getExtension = (fileName: string): string => {
  const lastDot = fileName.lastIndexOf('.')
  return lastDot > -1 ? fileName.slice(lastDot + 1).toLowerCase() : ''
}

/**
 * 格式化时间显示
 * 输入格式：YYYY-MM-DD HH:mm:ss
 * - 今天：显示 HH:mm
 * - 本年非今天：显示 MM-DD HH:mm
 * - 非本年：显示 YYYY-MM-DD HH:mm
 */
const formatTime = (dateStr?: string): string => {
  if (!dateStr) return ''

  // 直接截取，避免不必要的 split 操作
  const datePart = dateStr.slice(0, 10)
  const timePart = dateStr.slice(11, 16) // HH:mm

  if (datePart.length < 10) return ''

  // 一次性解析日期部分
  const year = datePart.slice(0, 4)
  const month = datePart.slice(5, 7)
  const day = datePart.slice(8, 10)

  const now = new Date()
  const currentYear = String(now.getFullYear())

  // 今日判断：比较时间戳（最高效）
  const todayStr = `${currentYear}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

  if (datePart === todayStr) {
    return timePart
  }

  if (year === currentYear) {
    return `${month}/${day} ${timePart}`
  }

  return `${year}/${month}/${day} ${timePart}`
}

const props = defineProps<{
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool' | 'error' | 'thinking'
  content: string
  currentIndex: number
  totalMessages: number
  createdAt?: string
  agentHasResult?: boolean
  isStreaming?: boolean
}>()

defineEmits<{
  inputTagPreview: [value: unknown]
  interactionSubmit: [payload: InteractionSubmitPayload]
  uipRetry: [uipCode: string]
  vepRetry: [vepCode: string]
}>()

const isUser = computed(() => props.role === 'user')
const isThinking = computed(() => props.role === 'thinking')
const isAssistant = computed(() => props.role === 'assistant')
const isTool = computed(() => props.role === 'tool')
const isError = computed(() => props.role === 'error')

const parsedUserContent = computed(() => parseUserContent(props.content))
const formattedTime = computed(() => formatTime(props.createdAt))

// 预览相关状态
const previewVisible = ref(false)
const previewCurrentIndex = ref(0)

// 推理面板展开状态
const reasoningExpanded = ref(false)

watch(() => props.currentIndex === props.totalMessages - 1, (thinking) => {
    reasoningExpanded.value = thinking
}, { immediate: true })

// 工具调用面板展开状态（默认收起）
const toolExpanded = ref(false)

/** 工具调用类型 */
interface ToolCallItem {
  name: string
  totalTimes: number
  args: string
  result: string
}

const { resolveToolCallName } = useToolCallDisplayName()

/** 解析工具调用 JSON 内容 */
const parsedToolCall = computed<ToolCallItem>(() => {
  if (!props.content) return null
  try {
    return JSON.parse(props.content)
  } catch {
    return null
  }
})

/**
 * 工具是否失败（内容启发式：TOOL 消息无显式状态位）：
 * - agentscope 错误约定：ToolResultBlock.error 的 "Error:" 前缀 / 执行层 "Tool execution failed"
 * - 内置工具结构化返回的 "status":"failed"（如 web_search）
 */
const toolFailed = computed(() => {
  const r = parsedToolCall.value?.result
  if (!r) return false
  const t = String(r).trim()
  return t.startsWith('Error:')
    || t.includes('Tool execution failed')
    || /"status"\s*:\s*"failed"/.test(t)
})

// 请求参数/响应结果小节折叠态（默认都展开，可各自收起）
const argsExpanded = ref(true)
const resultExpanded = ref(true)
// 小节复制反馈（2 秒内显示对勾）
const copiedSection = ref<'args' | 'result' | null>(null)

/** JSON 美化：能解析则两空格缩进，否则原样返回（结果可能是纯文本） */
function prettyJson(text?: string): string {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}
const prettyArgs = computed(() => prettyJson(parsedToolCall.value?.args))
const prettyResult = computed(() => prettyJson(parsedToolCall.value?.result))

/** 复制小节原始内容（未美化的原文，便于排查） */
async function copySection(section: 'args' | 'result') {
  const raw = section === 'args' ? parsedToolCall.value?.args : parsedToolCall.value?.result
  if (!raw) return
  try {
    await navigator.clipboard.writeText(raw)
    copiedSection.value = section
    setTimeout(() => {
      if (copiedSection.value === section) copiedSection.value = null
    }, 2000)
  } catch {
    // 剪贴板不可用时静默
  }
}

// 复制成功状态（2秒内）
const copied = ref(false)

/**
 * 待复制的文本内容
 * 用户消息：文件名列表 + 文本；AI消息：仅正文（不包含推理过程）
 */
const copyText = computed(() => {
  if (isUser.value) {
    const { files, text } = parsedUserContent.value
    if (files.length === 0) return text
    const fileNames = files.map(f => f.name).join('\n')
    return text ? `${fileNames}\n${text}` : fileNames
  }
  return props.content
})

/**
 * 复制消息内容到剪贴板
 * 使用 Clipboard API，失败时降级到 execCommand
 */
async function handleCopy() {
  if (copied.value || !copyText.value) return
  const text = copyText.value
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      throw new Error('clipboard unavailable')
    }
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
    } catch {
      // 复制失败静默处理
    }
    document.body.removeChild(textarea)
  }
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

/**
 * 打开文件预览
 */
const openPreview = (index: number) => {
  previewCurrentIndex.value = index
  previewVisible.value = true
}
</script>

<template>
  <div class="chat-message" :class="[isUser ? 'chat-message-user' : 'chat-message-assistant']" :data-msg-id="isUser ? id : undefined">
    <template v-if="isUser">
      <div class="chat-message-bubble chat-message-bubble_user" style="position: relative">
        <div class="message-time">{{ formattedTime }}</div>
        <!-- 文件列表 -->
        <div v-if="parsedUserContent.files.length > 0" class="chat-message-files">
          <div
            v-for="(item, index) in parsedUserContent.files"
            :key="item.id"
            @click="openPreview(index)"
            class="chat-message-file-item"
          >
            <MediaIcon :type="(item.extension ?? getExtension(item.name)) || 'FILE'" size="19"/>
            <span class="chat-message-file-name" :title="item.name">{{ item.name }}</span>
          </div>
        </div>
        <!-- 文本内容（支持标签渲染） -->
        <span v-if="parsedUserContent.text" class="chat-message-user-content">
          <TaggedContentRenderer
            @inputTagPreview="$emit('inputTagPreview', $event)"
            :content="parsedUserContent.text" />
        </span>
        <!-- 复制按钮：悬浮显现于气泡左侧 -->
        <span
          class="msg-copy-btn msg-copy-btn--user"
          :class="{ 'is-done': copied }"
          :title="copied ? '已复制' : '复制'"
          @click="handleCopy"
        >
          <CheckOutlined v-if="copied" />
          <CopyOutlined v-else />
        </span>
      </div>
    </template>
    <template v-else-if="isThinking">
      <div class="chat-message-bubble">
        <div v-if="!agentHasResult && !content" class="chat-loading-dots">
          <span></span><span></span><span></span>
        </div>
        <!-- 推理过程面板（独立于正文显示） -->
        <div v-if="isThinking" class="chat-reasoning-panel">
          <div class="chat-reasoning-header" @click="reasoningExpanded = !reasoningExpanded">
            <span class="chat-reasoning-icon">
              <LoadingOutlined v-if="isStreaming" spin />
              <BulbOutlined v-else />
            </span>
            <span class="chat-reasoning-title">
              {{ isStreaming ? '思考中...' : '思考过程' }}
            </span>
            <span class="chat-reasoning-arrow">
              <DownOutlined v-if="reasoningExpanded" />
              <RightOutlined v-else />
            </span>
          </div>
          <!-- 思考内容是标准 Markdown（列表/加粗/行内代码），按 markdown 渲染恢复结构；
               故意不包 chat-md-content 类：让元素继承本容器的灰色小字弱化样式，结构成型但不抢正文的戏 -->
          <div class="chat-reasoning-content" :class="{ 'is-expanded': reasoningExpanded }">
            <MarkdownRenderer :content="content" :is-streaming="isStreaming" />
          </div>
        </div>
      </div>
    </template>
    <template v-else-if="isAssistant">
      <div class="chat-message-bubble">
        <div v-if="!agentHasResult && !content" class="chat-loading-dots">
          <span></span><span></span><span></span>
        </div>
        <!-- 正文内容 -->
        <div v-if="isAssistant" class="chat-md-content">
          <MarkdownRenderer
            :content="content"
            :is-streaming="isStreaming"
            :disabled="currentIndex !== totalMessages - 1"
            @interaction-submit="$emit('interactionSubmit', $event)"
            @uip-retry="$emit('uipRetry', $event)"
            @vep-retry="$emit('vepRetry', $event)" />
          <!-- 复制按钮：悬浮显现于正文下方 -->
          <span
            v-if="content"
            class="msg-copy-btn msg-copy-btn--assistant"
            :class="{ 'is-done': copied }"
            :title="copied ? '已复制' : '复制'"
            @click="handleCopy"
          >
            <CheckOutlined v-if="copied" />
            <CopyOutlined v-else />
          </span>
        </div>
      </div>
    </template>
    <template v-else-if="isTool">
      <div class="chat-message-bubble">
        <div class="chat-tool-panel">
          <!-- 可点击的头部：图标 + 标题（含工具名） + 状态耗时 + 展开/收起箭头 -->
          <div class="chat-tool-header" @click="toolExpanded = !toolExpanded">
            <span class="chat-tool-header-icon"><ToolOutlined /></span>
            <span class="chat-tool-header-title">
              工具调用<template v-if="parsedToolCall">：{{ resolveToolCallName(parsedToolCall.name) }}</template>
            </span>
            <span
              v-if="parsedToolCall"
              class="chat-tool-header-status"
              :class="toolFailed ? 'chat-tool-status--fail' : 'chat-tool-status--ok'"
            >
              {{ toolFailed ? '失败' : '完成' }} · {{ formatElapsed(parsedToolCall.totalTimes) }}
            </span>
            <span class="chat-tool-header-arrow">
              <DownOutlined v-if="toolExpanded" />
              <RightOutlined v-else />
            </span>
          </div>

          <!-- 展开后的工具调用详情（名称/耗时已在标题行）：请求参数（默认收起）+ 响应结果 -->
          <div class="chat-tool-body" :class="{ 'is-expanded': toolExpanded }">
            <template v-if="parsedToolCall">
              <div v-if="parsedToolCall.args && parsedToolCall.args !== '{}'" class="chat-tool-section">
                <div class="chat-tool-section-header">
                  <span class="chat-tool-section-label chat-tool-section-label--clickable" @click="argsExpanded = !argsExpanded">
                    <span class="chat-tool-section-arrow">
                      <DownOutlined v-if="argsExpanded" />
                      <RightOutlined v-else />
                    </span>
                    请求参数
                  </span>
                  <span class="chat-tool-section-copy" :title="copiedSection === 'args' ? '已复制' : '复制'" @click.stop="copySection('args')">
                    <CheckOutlined v-if="copiedSection === 'args'" />
                    <CopyOutlined v-else />
                  </span>
                </div>
                <pre v-show="argsExpanded" class="chat-tool-item-code">{{ prettyArgs }}</pre>
              </div>
              <div v-if="parsedToolCall.result" class="chat-tool-section">
                <div class="chat-tool-section-header">
                  <span class="chat-tool-section-label chat-tool-section-label--clickable" @click="resultExpanded = !resultExpanded">
                    <span class="chat-tool-section-arrow">
                      <DownOutlined v-if="resultExpanded" />
                      <RightOutlined v-else />
                    </span>
                    响应结果
                  </span>
                  <span class="chat-tool-section-copy" :title="copiedSection === 'result' ? '已复制' : '复制'" @click.stop="copySection('result')">
                    <CheckOutlined v-if="copiedSection === 'result'" />
                    <CopyOutlined v-else />
                  </span>
                </div>
                <pre v-show="resultExpanded" class="chat-tool-item-code">{{ prettyResult }}</pre>
              </div>
            </template>
            <!-- JSON 解析失败：降级为原始文本 -->
            <pre v-else class="chat-tool-raw">{{ content }}</pre>
          </div>
        </div>
      </div>
    </template>
    <template v-else-if="isError">
      <div class="chat-message-bubble">
        <div class="chat-md-content">
          <span class="error-text">{{ content }}</span>
        </div>
      </div>
    </template>
    <!-- 媒体预览组件 -->
    <MediaPreview
      v-if="previewVisible"
      v-model:visible="previewVisible"
      :items="parsedUserContent.files"
      :current-index="previewCurrentIndex"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

.message-time {
  position: absolute;
  top: -18px;
  right: 3px;
  width: 150px;
  text-align: end;
  font-size: var(--font-size-xs);
  color: #d2d2d2;
}

.error-text {
  color: tomato;
}

.chat-message-files {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.chat-message-file-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 280px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
  cursor: pointer;
  &:hover {
    background: rgba(255, 255, 255, 0.9);
  }
}

.chat-message-file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/**
 * 复制按钮：悬浮显现 + 图标状态反馈
 * 常态隐藏，鼠标悬停消息气泡时渐显，点击后图标切换为对勾并保持2秒
 */
.msg-copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--border-radius-sm);
  font-size: 13px;
  color: #a0a4ab;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, color 0.15s ease, background-color 0.15s ease;

  &:hover {
    color: #4a4f57;
  }

  &.is-done {
    color: #52c41a;
    opacity: 1;
  }
}

.msg-copy-btn--user {
  position: absolute;
  right: 0;
  bottom: -25px;
}

.msg-copy-btn--assistant {
  position: absolute;
  left: 10px;
  bottom: -20px;
}

/* 确保气泡作为绝对定位参照 */
.chat-message-assistant .chat-message-bubble {
  position: relative;
}

/* 悬停消息气泡时显示复制按钮 */
.chat-message-bubble:hover .msg-copy-btn {
  opacity: 1;
}
</style>

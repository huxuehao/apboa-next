<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { LoadingOutlined, BulbOutlined, CopyOutlined, CheckOutlined, ToolOutlined, RightOutlined, DownOutlined, CheckCircleFilled, CloseCircleFilled, ClockCircleOutlined, DeploymentUnitOutlined, RetweetOutlined, ThunderboltOutlined, SoundOutlined, PauseCircleOutlined } from '@ant-design/icons-vue'
import { useTtsPlayback } from '@/composables/chat/useTtsPlayback'
import MediaPreview from '@/components/common/MediaPreview.vue'
import type { UploadedFileItem } from '@/types'
import MediaIcon from '@/components/common/MediaIcon.vue'
import AttachImage from '@/components/common/AttachImage.vue'
import { isImageExtension } from '@/utils/chat/attachImage'
import MarkdownRenderer from "@/components/markdown/MarkdownRenderer.vue";
import TaggedContentRenderer from './TaggedContentRenderer.vue';
import ErrorMessageCard from './ErrorMessageCard.vue';
import SubProcessSteps from './SubProcessSteps.vue';
import ToolConfirmPanel from './confirm/ToolConfirmPanel.vue';
import type { SubProcessStep } from '@/types';
import type { MarkdownInteractionSubmitPayload } from '@/components/markdown/types'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'
import { formatElapsed, fmtFullTime, fmtRelativeTime, fmtDuration, fmtTokens, fmtTokensPerSec } from '@/utils/chat/format'
import { vStickBottom } from '@/utils/chat/stickBottom'

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

const props = defineProps<{
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool' | 'error' | 'thinking'
  content: string
  currentIndex: number
  totalMessages: number
  createdAt?: string
  meta?: string
  agentHasResult?: boolean
  isStreaming?: boolean
  /** 会话 agent 是否绑定了语音合成模型（未绑定不显示朗读按钮） */
  ttsEnabled?: boolean
}>()

defineEmits<{
  inputTagPreview: [value: unknown]
  interactionSubmit: [payload: MarkdownInteractionSubmitPayload]
  uipRetry: [uipCode: string]
  vepRetry: [vepCode: string]
}>()

const { speakingMessageId, speakMessage, interrupt: interruptSpeak } = useTtsPlayback()
/** 本条消息是否正处于手动朗读中，按钮在朗读/停止两态间切换 */
const isSpeakingThis = computed(() => speakingMessageId.value === props.id)

function handleSpeak() {
  if (isSpeakingThis.value) {
    interruptSpeak()
  } else {
    speakMessage(props.id, props.content)
  }
}

const isUser = computed(() => props.role === 'user')
const isThinking = computed(() => props.role === 'thinking')
const isAssistant = computed(() => props.role === 'assistant')
const isTool = computed(() => props.role === 'tool')
const isError = computed(() => props.role === 'error')

const parsedUserContent = computed(() => parseUserContent(props.content))
// 常态显示相对时间（刚刚 / X 分钟前 / 昨天 HH:mm…），悬停 Tooltip 展示完整时间
const relativeTime = computed(() => fmtRelativeTime(props.createdAt))
const fullTime = computed(() => fmtFullTime(props.createdAt))

/** 消息元数据类型（后端 ChatLogHook 写入的 run 级统计） */
interface MessageMeta {
  durationMs?: number
  iterationCount?: number
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  /** 本次回复实际使用的模型（消息级审计） */
  modelLabel?: string
}

// assistant 正文的 run 级元数据：耗时 / 推理轮次 / token 用量（历史消息即有，当轮流式结束后由补拉填充）。
// 后端 Jackson 把 Long 序列化为字符串（防精度丢失），统一归一化为 number；0/非法值归 undefined 让 v-if 兜底不展示
const parsedMeta = computed<MessageMeta | null>(() => {
  if (!props.meta) return null
  try {
    const raw = JSON.parse(props.meta) as Record<string, unknown>
    const num = (v: unknown): number | undefined => {
      const n = Number(v)
      return Number.isFinite(n) && n > 0 ? n : undefined
    }
    return {
      durationMs: num(raw.durationMs),
      iterationCount: num(raw.iterationCount),
      inputTokens: num(raw.inputTokens),
      outputTokens: num(raw.outputTokens),
      totalTokens: num(raw.totalTokens),
      modelLabel: typeof raw.modelLabel === 'string' && raw.modelLabel ? raw.modelLabel : undefined,
    }
  } catch {
    return null
  }
})
const tokensPerSec = computed(() =>
  fmtTokensPerSec(parsedMeta.value?.outputTokens, parsedMeta.value?.totalTokens, parsedMeta.value?.durationMs))

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

/** 工具调用类型（subProcess 与实时步骤同构，渲染统一走 SubProcessSteps 组件） */
interface ToolCallItem {
  name: string
  totalTimes: number
  args: string
  result: string
  subProcess?: SubProcessStep[]
  /** HITL 确认态（落库 confirmState）：approved=经授权执行（含一键授权）/ rejected=被拒绝 */
  confirmState?: 'approved' | 'rejected'
}

/**
 * 工具结果是否失败（内容启发式：TOOL 消息无显式状态位）：
 * agentscope "Error:" 前缀 / 执行层 "Tool execution failed" / 结构化 "status":"failed"
 */
function isToolResultFailed(result?: string): boolean {
  if (!result) return false
  const t = String(result).trim()
  return t.startsWith('Error:')
    || t.includes('Tool execution failed')
    || /"status"\s*:\s*"failed"/.test(t)
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

/** 工具是否失败（内容启发式：TOOL 消息无显式状态位，与子过程工具步共用判定） */
const toolFailed = computed(() => isToolResultFailed(parsedToolCall.value?.result))

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
      <div class="chat-message-bubble chat-message-bubble_user">
        <!-- 文件列表 -->
        <div v-if="parsedUserContent.files.length > 0" class="chat-message-files">
          <template v-for="(item, index) in parsedUserContent.files" :key="item.id">
            <!-- 图片：保比例直接预览，点击放大 -->
            <div
              v-if="isImageExtension(item.extension || getExtension(item.name))"
              class="chat-message-image-item"
              @click="openPreview(index)"
            >
              <AttachImage
                class="chat-message-image"
                :attach-id="item.id"
                :name="item.name"
                :extension="item.extension || getExtension(item.name)"
              />
            </div>
            <!-- 其他类型：保持文件名 chip -->
            <div
              v-else
              @click="openPreview(index)"
              class="chat-message-file-item"
            >
              <MediaIcon :type="(item.extension ?? getExtension(item.name)) || 'FILE'" size="19"/>
              <span class="chat-message-file-name" :title="item.name">{{ item.name }}</span>
            </div>
          </template>
        </div>
        <!-- 文本内容（支持标签渲染） -->
        <span v-if="parsedUserContent.text" class="chat-message-user-content">
          <TaggedContentRenderer
            @inputTagPreview="$emit('inputTagPreview', $event)"
            :content="parsedUserContent.text" />
        </span>
      </div>
      <!-- footer：相对时间（悬停看完整时间）+ 复制按钮，气泡外右下角 -->
      <div class="chat-msg-footer">
        <a-tooltip v-if="relativeTime" :title="fullTime">
          <span class="chat-msg-time">{{ relativeTime }}</span>
        </a-tooltip>
        <span
          class="msg-copy-btn"
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
          <div v-stick-bottom="isStreaming === true" class="chat-reasoning-content" :class="{ 'is-expanded': reasoningExpanded }">
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
      <!-- footer：run 元数据（耗时/轮次/token）+ 相对时间（悬停看完整时间）+ 复制按钮，气泡外左下角 -->
      <div v-if="content" class="chat-msg-footer chat-msg-footer--assistant">
        <template v-if="parsedMeta">
          <a-tooltip v-if="parsedMeta.durationMs" :title="tokensPerSec ? `生成速率 ${tokensPerSec} token/s` : '本次回复总耗时'" :overlay-style="{ maxWidth: 'none' }">
            <span class="chat-msg-meta-item"><ClockCircleOutlined /> {{ fmtDuration(parsedMeta.durationMs) }}</span>
          </a-tooltip>
          <a-tooltip v-if="parsedMeta.iterationCount" title="模型推理轮数（含工具调用轮次）" :overlay-style="{ maxWidth: 'none' }">
            <span class="chat-msg-meta-item"><RetweetOutlined /> {{ parsedMeta.iterationCount }} 轮</span>
          </a-tooltip>
          <a-tooltip v-if="parsedMeta.totalTokens">
            <template #title>
              <div>输入 token：{{ fmtTokens(parsedMeta.inputTokens) }}</div>
              <div>输出 token：{{ fmtTokens(parsedMeta.outputTokens) }}</div>
              <div>合计：{{ fmtTokens(parsedMeta.totalTokens) }}</div>
            </template>
            <span class="chat-msg-meta-item"><ThunderboltOutlined /> {{ fmtTokens(parsedMeta.totalTokens) }} tokens</span>
          </a-tooltip>
          <a-tooltip v-if="parsedMeta.modelLabel" title="本次回复使用的模型" :overlay-style="{ maxWidth: 'none' }">
            <span class="chat-msg-meta-item"><DeploymentUnitOutlined /> {{ parsedMeta.modelLabel }}</span>
          </a-tooltip>
        </template>
        <a-tooltip v-if="relativeTime" :title="fullTime">
          <span class="chat-msg-time">{{ relativeTime }}</span>
        </a-tooltip>
        <span
          class="msg-copy-btn"
          :class="{ 'is-done': copied }"
          :title="copied ? '已复制' : '复制'"
          @click="handleCopy"
        >
          <CheckOutlined v-if="copied" />
          <CopyOutlined v-else />
        </span>
        <span
          v-if="ttsEnabled && !isStreaming"
          class="msg-copy-btn"
          :class="{ 'is-speaking': isSpeakingThis }"
          :title="isSpeakingThis ? '停止朗读' : '朗读'"
          @click="handleSpeak"
        >
          <PauseCircleOutlined v-if="isSpeakingThis" />
          <SoundOutlined v-else />
        </span>
      </div>
    </template>
    <template v-else-if="isTool">
      <div class="chat-message-bubble">
        <!-- HITL 定制确认卡只读回显：置于工具面板上方直接可见（无需展开），
             重现确认时的业务快照（args 为最终参数，含用户改参）；
             无定制渲染器的工具面板内部不渲染任何内容 -->
        <ToolConfirmPanel
          v-if="parsedToolCall?.confirmState"
          :name="parsedToolCall.name"
          :args="parsedToolCall.args"
          readonly
          :decided="parsedToolCall.confirmState"
          class="chat-tool-confirm-replay"
        />
        <div class="chat-tool-panel">
          <!-- 可点击的头部：行首状态圆标（形状+颜色双编码，扫视即辨） + 标题（含工具名） + 状态耗时 + 展开/收起箭头 -->
          <div class="chat-tool-header" @click="toolExpanded = !toolExpanded">
            <span class="chat-tool-header-icon">
              <template v-if="parsedToolCall">
                <CloseCircleFilled v-if="toolFailed" class="chat-tool-status--fail" title="失败" />
                <CheckCircleFilled v-else class="chat-tool-status--ok" title="完成" />
              </template>
              <ToolOutlined v-else />
            </span>
            <!-- 标题直接显示工具名（类型语义已由面板形态与圆标承担），解析失败降级回"工具调用" -->
            <span class="chat-tool-header-title">
              <template v-if="parsedToolCall">{{ resolveToolCallName(parsedToolCall.name) }}</template>
              <template v-else>工具调用</template>
            </span>
            <span
              v-if="parsedToolCall?.confirmState"
              class="chat-tool-confirm-badge"
              :class="parsedToolCall.confirmState === 'approved' ? 'chat-tool-confirm-badge--ok' : 'chat-tool-confirm-badge--no'"
            >
              {{ parsedToolCall.confirmState === 'approved' ? '已授权' : '已拒绝' }}
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
              <!-- 子智能体过程（公共组件，与实时卡片/日志页共用；在响应结果之前） -->
              <SubProcessSteps v-if="parsedToolCall.subProcess?.length" :steps="parsedToolCall.subProcess" />
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
        <ErrorMessageCard :content="content" />
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

/* HITL 确认态徽标（工具卡头部常显，收起也可见决策结果） */
.chat-tool-confirm-badge {
  flex: none;
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  margin-right: 8px;

  &--ok {
    background: #f6ffed;
    color: #389e0d;
  }

  &--no {
    background: #fff1f0;
    color: #cf1322;
  }
}

/* 定制确认卡只读回显区（工具面板上方直接可见） */
.chat-tool-confirm-replay {
  margin: 0 0 8px;
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

/* 图片附件：保比例预览（约束最大范围），点击放大 */
.chat-message-image-item {
  align-self: flex-start;
  cursor: zoom-in;

  :deep(.attach-image),
  :deep(.attach-image-skeleton) {
    max-width: min(320px, 100%);
    max-height: 280px;
    border: 1px solid rgba(0, 0, 0, 0.06);
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
 * 复制按钮：常亮 + 图标状态反馈
 * 点击后图标切换为对勾并保持2秒
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
  transition: color 0.15s ease, background-color 0.15s ease;

  &:hover {
    color: #4a4f57;
  }

  &.is-done {
    color: #52c41a;
  }

  &.is-speaking {
    color: var(--color-primary);
  }
}
</style>

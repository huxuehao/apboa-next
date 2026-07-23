<script setup lang="ts">
/**
 * 子智能体过程步骤列表（单一渲染实现，三处复用）：
 * - 流式中的工具卡片（ToolCallItem）：实时步骤逐步出现，工具步带 running 转圈
 * - 聊天页 / 日志会话页的历史工具卡片（MessageItem）：落库 subProcess
 * 实时与落库数据同构（后端 RunTelemetryExtractor 统一生成），本组件不感知来源。
 */
import { computed, onUnmounted, ref, watch } from 'vue'
import { CopyOutlined, CheckOutlined, RightOutlined, DownOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import type { SubProcessStep } from '@/types'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'
import { formatElapsed } from '@/utils/chat/format'

const props = defineProps<{
  steps: SubProcessStep[]
}>()

// 执行中工具步的耗时递增（100ms 刷新，与主工具卡片计时一致）；无 running 步时不跑计时器
const nowTick = ref(Date.now())
let tickTimer: number | null = null
const hasRunning = computed(() => props.steps.some((s) => s.running && s.startTime))
watch(hasRunning, (running) => {
  if (running && tickTimer == null) {
    nowTick.value = Date.now()
    tickTimer = window.setInterval(() => { nowTick.value = Date.now() }, 100)
  } else if (!running && tickTimer != null) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}, { immediate: true })
onUnmounted(() => {
  if (tickTimer != null) clearInterval(tickTimer)
})

const runningElapsed = (step: SubProcessStep): string => {
  if (!step.startTime) return ''
  const s = Math.max(0, nowTick.value - step.startTime) / 1000
  return `${s.toFixed(1)}s`
}

/**
 * "生成中"只标在最后一个流式步上：思考与回复在同一轮内先后流式生成，轮末才统一定稿，
 * 回复开始吐字（新步追加到尾部）即说明思考已结束，思考步的转圈应立即消失
 */
const isActivelyStreaming = (step: SubProcessStep, i: number): boolean =>
  !!step.streaming && i === props.steps.length - 1

const { resolveToolCallName } = useToolCallDisplayName()

/** 步骤类型 → 中文标签 */
const SUB_STEP_LABELS: Record<string, string> = {
  thinking: '思考',
  text: '回复',
  tool: '调用工具',
  tool_use: '调用工具',
  tool_result: '工具结果',
  error: '错误',
}
const subStepLabel = (type: string) => SUB_STEP_LABELS[type] ?? type

/**
 * 工具结果是否失败（与主工具卡的启发式一致）：
 * agentscope "Error:" 前缀 / 执行层 "Tool execution failed" / 结构化 "status":"failed"
 */
function isToolResultFailed(result?: string): boolean {
  if (!result) return false
  const t = String(result).trim()
  return t.startsWith('Error:')
    || t.includes('Tool execution failed')
    || /"status"\s*:\s*"failed"/.test(t)
}

/** JSON 美化：能解析则两空格缩进，否则原样返回 */
function prettyJson(text?: string): string {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

// 每步独立折叠态（默认收起）+ 复制反馈（key：`${i}` 普通步 / `${i}-args` / `${i}-result`）
const expanded = ref<Record<number, boolean>>({})
const toggle = (i: number) => {
  expanded.value[i] = !expanded.value[i]
}

// 进行中的步骤自动展开（仅未被用户手动操作过时）：流式思考/回复的逐字增长、
// 执行中工具步的请求参数即时可见；历史数据无实时标记，维持默认收起
watch(
  () => props.steps.map((s) => s.streaming || s.running),
  () => {
    props.steps.forEach((s, i) => {
      if ((s.streaming || s.running) && expanded.value[i] === undefined) {
        expanded.value[i] = true
      }
    })
  },
  { immediate: true }
)
const copiedKey = ref<string | null>(null)
async function copyContent(key: string, raw?: string) {
  if (!raw) return
  try {
    await navigator.clipboard.writeText(raw)
    copiedKey.value = key
    setTimeout(() => {
      if (copiedKey.value === key) copiedKey.value = null
    }, 2000)
  } catch {
    // 剪贴板不可用时静默
  }
}
</script>

<template>
  <template v-if="steps.length">
    <div class="chat-tool-sub-group-title">子智能体过程</div>
    <div v-for="(step, i) in steps" :key="i" class="chat-tool-section">
      <!-- 工具步：迷你工具卡（标题 = 标签 + 工具名 + 状态耗时；展开 = 请求参数 / 响应结果） -->
      <template v-if="step.type === 'tool'">
        <div class="chat-tool-section-header">
          <span class="chat-tool-section-label chat-tool-section-label--clickable" @click="toggle(i)">
            <span class="chat-tool-section-arrow">
              <DownOutlined v-if="expanded[i]" />
              <RightOutlined v-else />
            </span>
            <span class="chat-tool-sub-step-tag is-tool">{{ subStepLabel(step.type) }}</span>
            <span class="chat-tool-sub-step-name">{{ resolveToolCallName(step.name || '') }}</span>
            <span v-if="step.running" class="chat-tool-sub-step-status chat-tool-sub-step-status--running">
              <LoadingOutlined spin /> 执行中<template v-if="runningElapsed(step)"> · {{ runningElapsed(step) }}</template>
            </span>
            <span
              v-else
              class="chat-tool-sub-step-status"
              :class="isToolResultFailed(step.result) ? 'chat-tool-status--fail' : 'chat-tool-status--ok'"
            >
              {{ isToolResultFailed(step.result) ? '失败' : '完成' }}<template v-if="step.elapsed != null"> · {{ formatElapsed(step.elapsed) }}</template>
            </span>
          </span>
        </div>
        <template v-if="expanded[i]">
          <div v-if="step.args && step.args !== '{}'" class="chat-tool-sub-inner">
            <div class="chat-tool-section-header">
              <span class="chat-tool-section-label">请求参数</span>
              <span class="chat-tool-section-copy" :title="copiedKey === i + '-args' ? '已复制' : '复制'" @click.stop="copyContent(i + '-args', step.args)">
                <CheckOutlined v-if="copiedKey === i + '-args'" />
                <CopyOutlined v-else />
              </span>
            </div>
            <pre class="chat-tool-item-code">{{ prettyJson(step.args) }}</pre>
          </div>
          <div v-if="step.result" class="chat-tool-sub-inner">
            <div class="chat-tool-section-header">
              <span class="chat-tool-section-label">响应结果</span>
              <span class="chat-tool-section-copy" :title="copiedKey === i + '-result' ? '已复制' : '复制'" @click.stop="copyContent(i + '-result', step.result)">
                <CheckOutlined v-if="copiedKey === i + '-result'" />
                <CopyOutlined v-else />
              </span>
            </div>
            <pre class="chat-tool-item-code">{{ prettyJson(step.result) }}</pre>
          </div>
        </template>
      </template>
      <!-- 普通步（思考/回复/错误，兼容 v1 旧数据降级）：标签标题 + 复制；展开显示全文 -->
      <template v-else>
        <div class="chat-tool-section-header">
          <span class="chat-tool-section-label chat-tool-section-label--clickable" @click="toggle(i)">
            <span class="chat-tool-section-arrow">
              <DownOutlined v-if="expanded[i]" />
              <RightOutlined v-else />
            </span>
            <span class="chat-tool-sub-step-tag" :class="'is-' + step.type">{{ subStepLabel(step.type) }}</span>
            <span v-if="step.name" class="chat-tool-sub-step-name">{{ resolveToolCallName(step.name) }}</span>
            <span v-if="isActivelyStreaming(step, i)" class="chat-tool-sub-step-status chat-tool-sub-step-status--running">
              <LoadingOutlined spin /> 生成中
            </span>
          </span>
          <span class="chat-tool-section-copy" :title="copiedKey === String(i) ? '已复制' : '复制'" @click.stop="copyContent(String(i), step.content ?? step.args ?? step.result)">
            <CheckOutlined v-if="copiedKey === String(i)" />
            <CopyOutlined v-else />
          </span>
        </div>
        <pre v-show="expanded[i]" class="chat-tool-item-code chat-tool-sub-full">{{ step.content ?? step.args ?? step.result }}</pre>
      </template>
    </div>
  </template>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;
</style>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { CheckCircleOutlined } from '@ant-design/icons-vue'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'
import SubProcessSteps from './SubProcessSteps.vue'
import ToolConfirmPanel from './confirm/ToolConfirmPanel.vue'
import type { ConfirmFieldMeta, SubProcessStep } from '@/types'
import { vStickBottom } from '@/utils/chat/stickBottom'
import { formatElapsed } from '@/utils/chat/format'

const props = defineProps<{
  id: string,
  name: string
  args?: string
  result?: string
  elapsed?: number
  loading?: boolean
  /** 工具已真正执行完（TOOL_FINISHED 即时事件）——结果详情未到但状态与耗时已定格 */
  finished?: boolean
  /** 串行排队中（前面还有未完成的工具，本工具尚未开始执行） */
  queued?: boolean
  needConfirm?: boolean
  /** 待确认工具的参数字段元数据（随 TOOL_CONFIRM_REQUIRED / pending 下发，驱动确认表单） */
  confirmFields?: ConfirmFieldMeta[]
  /** 决策后业务摘要（定制确认卡提供，useChatStream 持有——组件会被 resume 续跑重建，不能本地存） */
  confirmSummary?: string
  startTime?: number
  /** 子智能体实时过程步骤（SUBAGENT_STEP 事件装配，与落库 subProcess 同构） */
  subSteps?: SubProcessStep[]
}>()

const { resolveToolCallName } = useToolCallDisplayName()

/** 仅显示层翻译（工具/子智能体 → 显示名）；HITL 允许/禁止回传仍用原始 name */
const displayName = computed(() => resolveToolCallName(props.name))

// 执行中实时耗时（100ms 刷新，0.1s 步进滚动）；needConfirm 暂停态不计时（工具没在跑，是在等人）；
// queued 排队态不计时（尚未开始执行，startTime 会在真正开始时被上游重置）
const nowTick = ref(Date.now())
let elapsedTimer: number | null = null

const startTick = () => {
  if (elapsedTimer != null) return
  nowTick.value = Date.now()
  elapsedTimer = window.setInterval(() => { nowTick.value = Date.now() }, 100)
}
const stopTick = () => {
  if (elapsedTimer != null) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

watch(
  () => props.loading && !props.needConfirm && !props.queued,
  (running) => { running ? startTick() : stopTick() },
  { immediate: true }
)
onUnmounted(stopTick)

const runningElapsed = computed(() => {
  if (!props.startTime) return ''
  const s = Math.max(0, nowTick.value - props.startTime) / 1000
  return `${s.toFixed(1)}s`
})

/** 完成态定格耗时（TOOL_FINISHED 携带的单工具真实值，与落库同源） */
const finishedElapsed = computed(() =>
  props.elapsed != null ? formatElapsed(props.elapsed) : ''
)

const emit = defineEmits<{
  (e: 'toolContent', value: any): void
  (e: 'subConfirm', value: { subToolUseId: string; approved: boolean }): void
}>()

// 执行中的请求参数（流式增量拼接中可能是不完整 JSON，原样展示；完整后自然美化）
const prettyRunningArgs = computed(() => {
  if (!props.args || props.args === '{}') return ''
  try {
    return JSON.stringify(JSON.parse(props.args), null, 2)
  } catch {
    return props.args
  }
})

/**
 * 确认决策（§6.5）：仅记录决策，工具实际由后端 resume 续跑执行（天然带租户/MCP 上下文）。
 * input 为用户在确认 UI 中修改后的参数（未修改则缺省，后端沿用模型原始参数）；
 * summary 为定制渲染器提供的业务摘要，经 decideConfirm 存回工具项后以 props 回流渲染。
 */
const handleDecide = (v: { approved: boolean; input?: Record<string, unknown>; summary?: string }) => {
  emit('toolContent', { toolUseId: props.id, name: props.name, approved: v.approved, input: v.input, summary: v.summary })
}
</script>

<template>
  <div>
    <div class="chat-tool-call" :class="{ 'chat-tool-call--loading': loading && !queued, 'chat-tool-call--queued': queued, 'chat-tool-call--finished': finished }">
      <span class="chat-tool-call-dot"></span>
      <span class="chat-tool-call-label">
      <template v-if="finished">
        已完成 {{ displayName }}<span v-if="finishedElapsed" class="chat-tool-call-elapsed"> · {{ finishedElapsed }}</span>
      </template>
      <template v-else-if="queued">
        等待执行 {{ displayName }}
      </template>
      <template v-else-if="needConfirm">
        等待授权 {{ displayName }}
      </template>
      <template v-else-if="loading">
        正在执行 {{ displayName }}<span v-if="runningElapsed" class="chat-tool-call-elapsed"> · {{ runningElapsed }}</span>
      </template>
    </span>
    </div>
    <!-- HITL 确认面板：定制渲染器 → schema 通用表单 → JSON 兜底（决策/改参经 toolContent 冒泡） -->
    <div v-if="needConfirm" class="chat-tool-call-confirm">
      <ToolConfirmPanel :name="name" :args="args" :fields="confirmFields" @decide="handleDecide" />
    </div>
    <!-- 决策后业务摘要回显（定制渲染器提供）：确认卡收起后保留一行"确认了什么" -->
    <div v-else-if="confirmSummary" class="chat-tool-call-summary">
      <CheckCircleOutlined class="chat-tool-call-summary-icon" /> 已确认 {{ confirmSummary }}
    </div>
    <!-- 执行中实时详情：请求参数（流式增量）+ 子智能体过程步骤（逐步出现） -->
    <div v-if="loading && !needConfirm && (prettyRunningArgs || subSteps?.length)" class="chat-tool-call-live">
      <div v-if="prettyRunningArgs" class="chat-tool-section">
        <div class="chat-tool-section-header">
          <span class="chat-tool-section-label">请求参数</span>
        </div>
        <pre v-stick-bottom class="chat-tool-item-code">{{ prettyRunningArgs }}</pre>
      </div>
      <SubProcessSteps v-if="subSteps?.length" :steps="subSteps" @sub-confirm="emit('subConfirm', $event)" />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

/* 等宽数字：计时滚动时不左右抖动 */
.chat-tool-call-elapsed {
  font-variant-numeric: tabular-nums;
}

/* 完成态：绿点定格（结果详情未到，但工具已真正跑完） */
.chat-tool-call--finished .chat-tool-call-dot {
  background: #52c41a;
  animation: none;
}

/* 排队态：灰点不动（串行执行，前面的工具还没跑完） */
.chat-tool-call--queued .chat-tool-call-dot {
  background: #d9d9d9;
  animation: none;
}
.chat-tool-call--queued .chat-tool-call-label {
  color: rgba(0, 0, 0, 0.45);
}

/* 执行中实时详情区：与完成后工具卡片体的内边距对齐 */
.chat-tool-call-live {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 10px 6px 22px;
}

/* HITL 确认面板区：与实时详情区同款缩进，面板宽度不撑满整行 */
.chat-tool-call-confirm {
  padding: 2px 10px 6px 22px;
  max-width: 560px;
}

/* 决策后业务摘要行：确认卡收起后的一行只读回显 */
.chat-tool-call-summary {
  padding: 2px 10px 6px 22px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.5);
}

.chat-tool-call-summary-icon {
  color: #52c41a;
  margin-right: 4px;
}
</style>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'
import SubProcessSteps from './SubProcessSteps.vue'
import type { SubProcessStep } from '@/types'
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

const foldArgs = ref<boolean>(true)

// 执行中的请求参数（流式增量拼接中可能是不完整 JSON，原样展示；完整后自然美化）
const prettyRunningArgs = computed(() => {
  if (!props.args || props.args === '{}') return ''
  try {
    return JSON.stringify(JSON.parse(props.args), null, 2)
  } catch {
    return props.args
  }
})

/** 允许：仅记录决策（§6.5），工具实际由后端 resume 续跑执行（天然带租户/MCP 上下文） */
const handleConfirm = (id: string, name: string) => {
  emit('toolContent', { toolUseId: id, name, approved: true })
}

/** 禁止：仅记录决策（§6.5），后端 resume 时喂入「拒绝授权」错误结果，不再前端塞文本 */
const handleCancel = (id: string, name: string) => {
  emit('toolContent', { toolUseId: id, name, approved: false })
}

/*查看参数*/
const handleShowArgs = () => {
  foldArgs.value = !foldArgs.value
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
      <template v-else-if="loading">
        正在执行 {{ displayName }}<span v-if="!needConfirm && runningElapsed" class="chat-tool-call-elapsed"> · {{ runningElapsed }}</span>
      </template>
      <template v-if="needConfirm" >
        <div class="chat-tool-call-actions">
          <AButton v-if="args && args !== '{}'"
                   type="link"
                   size="small"
                   @click="handleShowArgs">
            {{ `${foldArgs ? '展开参数' : '折叠参数'}` }}
          </AButton>
          <AButton type="primary" size="small" @click="handleConfirm(id, name)">允许</AButton>
          <AButton size="small" @click="handleCancel(id, name)">禁止</AButton>
        </div>
      </template>
    </span>
    </div>
    <div class="chat-tool-call" v-if="args && args !== '{}' && !foldArgs">
      {{ args && args !== '{}' ? args : '无参数' }}
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
</style>

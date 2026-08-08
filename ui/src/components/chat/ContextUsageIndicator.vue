<script setup lang="ts">
import { computed } from 'vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import type { ContextUsageEvent } from '@/types'

const props = defineProps<{
  usage?: ContextUsageEvent['value'] | null
  compressionActive?: boolean
}>()

const ratio = computed(() => Math.min(1, Math.max(0, props.usage?.compressionPressure ?? props.usage?.ratio ?? 0)))
const percent = computed(() => Math.round(ratio.value * 100))
const color = computed(() => {
  if (props.compressionActive) return '#f59e0b'
  if (percent.value >= 90) return '#ef4444'
  if (percent.value >= 75) return '#f97316'
  if (percent.value >= 60) return '#eab308'
  return '#22a06b'
})

// 格式化 tooltip 为多行可读格式
const tooltipLines = computed(() => {
  const used = props.usage?.usedTokens ?? 0
  const total = props.usage?.totalTokens ?? 0
  const tokenThreshold = props.usage?.tokenThreshold ?? total
  const messageCount = props.usage?.messageCount ?? 0
  const messageThreshold = props.usage?.messageThreshold ?? 0
  const triggerReason = props.usage?.triggerReason === 'MESSAGE' ? '消息数量' : 'Token 数量'

  const status = props.compressionActive ? '记忆压缩中...' : `压缩压力 ${percent.value}%`

  return [
    status,
    `Token: ${used.toLocaleString()} / ${tokenThreshold.toLocaleString()}`,
    `消息: ${messageCount.toLocaleString()} / ${messageThreshold.toLocaleString()}`,
    `上限: ${total.toLocaleString()} tokens`,
    `触发因素: ${triggerReason}`
  ]
})

// 用于 ATooltip 的 title（支持 HTML）
const tooltipHtml = computed(() => {
  return tooltipLines.value.join('<br/>')
})
</script>

<template>
  <ATooltip v-if="usage" placement="bottom">
    <template #title>
      <div class="context-tooltip">
        <div v-for="line in tooltipLines" :key="line" class="tooltip-line">
          {{ line }}
        </div>
      </div>
    </template>
    <span
      class="context-usage-indicator"
      :class="{ 'is-compressing': compressionActive }"
      :style="{ '--context-color': color, '--context-percent': `${percent}%` }"
      role="status"
      aria-label="上下文压缩压力"
    >
      <LoadingOutlined v-if="compressionActive" spin class="context-usage-loading" />
      <span v-else class="context-usage-percent">{{ percent }}</span>
    </span>
  </ATooltip>
</template>

<style scoped lang="scss">
.context-usage-indicator {
  --context-color: #22a06b;
  --context-percent: 0%;
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-right: 7px;
  border-radius: 50%;
  background: conic-gradient(var(--context-color) var(--context-percent), #e8edf2 0);
  color: var(--context-color);
  font-size: 9px;
  font-weight: 600;
  flex: 0 0 28px;
  cursor: help;
}

.context-usage-indicator::after {
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: #fff;
  content: '';
}

.context-usage-percent,
.context-usage-loading {
  position: relative;
  z-index: 1;
}

.context-usage-loading {
  font-size: 13px;
}

.context-usage-indicator.is-compressing {
  animation: context-usage-pulse 1.2s ease-in-out infinite;
}

@keyframes context-usage-pulse {
  50% { transform: scale(1.08); }
}

// Tooltip 样式
.context-tooltip {
  .tooltip-line {
    padding: 1px 0;
    white-space: nowrap;

    &:not(:last-child) {
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      padding-bottom: 4px;
      margin-bottom: 4px;
    }
  }
}
</style>

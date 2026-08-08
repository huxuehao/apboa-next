<script setup lang="ts">
import { computed } from 'vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import type { ContextUsageEvent } from '@/types'

const props = defineProps<{
  usage?: ContextUsageEvent['value'] | null
  compressionActive?: boolean
}>()

const ratio = computed(() => Math.min(1, Math.max(0, props.usage?.ratio ?? 0)))
const percent = computed(() => Math.round(ratio.value * 100))
const color = computed(() => {
  if (props.compressionActive) return '#f59e0b'
  if (percent.value >= 90) return '#ef4444'
  if (percent.value >= 75) return '#f97316'
  if (percent.value >= 60) return '#eab308'
  return '#22a06b'
})
const tooltip = computed(() => {
  const used = props.usage?.usedTokens ?? 0
  const total = props.usage?.totalTokens ?? 0
  if (props.compressionActive) {
    return `记忆压缩中：已使用 ${used.toLocaleString()} / ${total.toLocaleString()} tokens`
  }
  return `已使用 ${used.toLocaleString()} / ${total.toLocaleString()} tokens`
})
</script>

<template>
  <ATooltip v-if="usage" placement="bottom" :title="tooltip">
    <span
      class="context-usage-indicator"
      :class="{ 'is-compressing': compressionActive }"
      :style="{ '--context-color': color, '--context-percent': `${percent}%` }"
      role="status"
      aria-label="上下文使用量"
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
</style>

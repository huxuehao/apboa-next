<script setup lang="ts">
/**
 * 进度环/完成率：展示当前值相对目标的完成百分比，支持环形与条形两种样式。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  data: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const rows = computed(() => props.data?.rows || [])
const mapping = computed(() => props.panel.fieldMapping || {})

const label = computed(() => (props.panel.options?.label as string) || '')
const barStyle = computed(() => (props.panel.options?.style as string) || 'ring')
const max = computed(() => Number(props.panel.options?.max) || 100)

const current = computed<number>(() => {
  const field = mapping.value.value as string | undefined
  const row = rows.value[0]
  if (field && row) {
    const n = Number(row[field])
    if (!Number.isNaN(n)) return n
  }
  const sv = Number(props.panel.options?.value)
  return Number.isNaN(sv) ? 0 : sv
})

const target = computed<number>(() => {
  const field = mapping.value.target as string | undefined
  const row = rows.value[0]
  if (field && row) {
    const n = Number(row[field])
    if (!Number.isNaN(n) && n !== 0) return n
  }
  return max.value
})

const percent = computed(() => (target.value > 0 ? (current.value / target.value) * 100 : 0))
const fillPct = computed(() => Math.min(100, Math.max(0, percent.value)))
const percentText = computed(() => Math.round(percent.value) + '%')

// 环形：r=42, 周长 = 2πr ≈ 263.9
const CIRCUMFERENCE = 263.9
const dashOffset = computed(() => CIRCUMFERENCE * (1 - fillPct.value / 100))
</script>

<template>
  <div class="progress-panel">
    <div v-if="error" class="pr-error">{{ error }}</div>

    <!-- 环形 -->
    <div v-else-if="barStyle === 'ring'" class="pr-ring-wrap">
      <svg class="pr-ring" viewBox="0 0 100 100">
        <circle cx="50" cy="50" r="42" fill="none" stroke="#f0f0f0" stroke-width="8" />
        <circle
          cx="50"
          cy="50"
          r="42"
          fill="none"
          stroke="#1677ff"
          stroke-width="8"
          stroke-linecap="round"
          :stroke-dasharray="CIRCUMFERENCE"
          :stroke-dashoffset="dashOffset"
          transform="rotate(-90 50 50)"
        />
      </svg>
      <div class="pr-center">
        <div class="pr-percent">{{ percentText }}</div>
        <div v-if="label" class="pr-label">{{ label }}</div>
      </div>
    </div>

    <!-- 条形 -->
    <div v-else class="pr-bar-wrap">
      <div class="pr-bar-head">
        <span v-if="label" class="pr-label">{{ label }}</span>
        <span class="pr-percent-sm">{{ percentText }}</span>
      </div>
      <div class="pr-bar-track">
        <div class="pr-bar-fill" :style="{ width: fillPct + '%' }" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.progress-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 4px 2px;
}

.pr-ring-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  max-width: 150px;
  max-height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pr-ring {
  width: 100%;
  height: 100%;

  circle {
    transition: stroke-dashoffset 0.6s ease;
  }
}

.pr-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.pr-percent {
  font-size: var(--dash-text-size, 24px);
  font-weight: 600;
  color: var(--dash-text-color, #1a1a1a);
}

.pr-label {
  font-size: 13px;
  color: #999;
}

.pr-bar-wrap {
  width: 100%;
}

.pr-bar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.pr-percent-sm {
  font-size: var(--dash-text-size, 14px);
  font-weight: 600;
  color: var(--dash-text-color, #1a1a1a);
}

.pr-bar-track {
  height: 10px;
  border-radius: 5px;
  background: #f0f0f0;
  overflow: hidden;
}

.pr-bar-fill {
  height: 100%;
  border-radius: 5px;
  background: #1677ff;
  transition: width 0.6s ease;
}

.pr-error {
  font-size: 13px;
  color: #cf1322;
}
</style>

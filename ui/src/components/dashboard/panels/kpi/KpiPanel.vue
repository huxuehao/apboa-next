<script setup lang="ts">
/**
 * KPI 趋势卡片：大数字 + 环比/同比涨跌 + 迷你 sparkline 折线。
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

/** 主数值：取值列的最后一行（最新），否则用静态值 */
const valueText = computed(() => {
  const field = mapping.value.value as string | undefined
  const row = rows.value.length ? rows.value[rows.value.length - 1] : undefined
  if (field && row) {
    const v = row[field]
    if (v !== null && v !== undefined) return formatNumber(v)
  }
  return (props.panel.options?.value as string) ?? '—'
})

const unit = computed(() => (props.panel.options?.unit as string) || '')
const labelText = computed(() => (props.panel.options?.label as string) || '')

/** 趋势序列：趋势列在各行的数值 */
const trend = computed<number[]>(() => {
  const field = mapping.value.trend as string | undefined
  if (!field) return []
  return rows.value
    .map((r) => Number(r[field]))
    .filter((n) => !Number.isNaN(n))
})

/** 环比：优先取 delta 列最后一行，否则由趋势首尾计算百分比 */
const delta = computed<number | null>(() => {
  const field = mapping.value.delta as string | undefined
  const row = rows.value.length ? rows.value[rows.value.length - 1] : undefined
  if (field && row) {
    const v = Number(row[field])
    return Number.isNaN(v) ? null : v
  }
  if (trend.value.length >= 2) {
    const first = trend.value[0] as number
    const last = trend.value[trend.value.length - 1] as number
    if (first === 0) return null
    return ((last - first) / Math.abs(first)) * 100
  }
  return null
})

const deltaUp = computed(() => (delta.value ?? 0) >= 0)

const deltaText = computed(() => {
  if (delta.value === null) return ''
  const sign = delta.value >= 0 ? '+' : ''
  return `${sign}${delta.value.toFixed(1)}%`
})

/** sparkline 折线点 */
const sparkPoints = computed(() => {
  const values = trend.value
  if (values.length < 2) return ''
  const w = 100
  const h = 28
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  return values
    .map((v, i) => {
      const x = (i / (values.length - 1)) * w
      const y = h - ((v - min) / span) * h
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

function formatNumber(v: unknown): string {
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString()
}
</script>

<template>
  <div class="kpi-panel">
    <div v-if="error" class="kpi-error">{{ error }}</div>
    <template v-else>
      <div v-if="labelText" class="kpi-label">{{ labelText }}</div>
      <div class="kpi-value-row">
        <span class="kpi-value">{{ valueText }}</span>
        <span v-if="unit" class="kpi-unit">{{ unit }}</span>
      </div>
      <div v-if="deltaText" class="kpi-delta" :class="deltaUp ? 'up' : 'down'">
        <span class="kpi-arrow">{{ deltaUp ? '▲' : '▼' }}</span>
        <span>{{ deltaText }}</span>
      </div>
      <svg v-if="sparkPoints" class="kpi-spark" viewBox="0 0 100 28" preserveAspectRatio="none">
        <polyline
          :points="sparkPoints"
          fill="none"
          :stroke="deltaUp ? '#52c41a' : '#cf1322'"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </template>
  </div>
</template>

<style scoped lang="scss">
.kpi-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding: 4px 2px;
}

.kpi-label {
  font-size: 13px;
  color: #999;
}

.kpi-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 4px;
}

.kpi-value {
  font-size: var(--dash-text-size, 30px);
  font-weight: var(--dash-text-weight, 600);
  color: var(--dash-text-color, #1a1a1a);
  line-height: 1.2;
}

.kpi-unit {
  font-size: 14px;
  color: #999;
}

.kpi-delta {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-top: 6px;
  font-size: 13px;

  &.up {
    color: #52c41a;
  }

  &.down {
    color: #cf1322;
  }
}

.kpi-arrow {
  font-size: 10px;
}

.kpi-spark {
  width: 100%;
  height: 28px;
  margin-top: 10px;
}

.kpi-error {
  font-size: 13px;
  color: #cf1322;
}
</style>

<script setup lang="ts">
/**
 * 数据卡片面板：展示单个关键指标（大数字 + 描述）。
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

const valueText = computed(() => {
  const mapping = props.panel.fieldMapping || {}
  const valueField = mapping.value as string | undefined
  const row = props.data?.rows?.[0]
  if (valueField && row) {
    const v = row[valueField]
    return v === null || v === undefined ? '—' : String(v)
  }
  return (props.panel.options?.value as string) ?? '—'
})

const labelText = computed(() => {
  const mapping = props.panel.fieldMapping || {}
  const labelField = mapping.label as string | undefined
  const row = props.data?.rows?.[0]
  if (labelField && row) {
    const v = row[labelField]
    if (v !== null && v !== undefined) return String(v)
  }
  return (props.panel.options?.label as string) ?? ''
})
</script>

<template>
  <div class="metric-panel">
    <div v-if="error" class="metric-error">{{ error }}</div>
    <template v-else>
      <div class="metric-value">{{ valueText }}</div>
      <div v-if="labelText" class="metric-label">{{ labelText }}</div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.metric-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding: 4px 2px;
}

.metric-value {
  font-size: 32px;
  font-weight: 600;
  color: #1a1a1a;
  line-height: 1.2;
  word-break: break-all;
}

.metric-label {
  margin-top: 8px;
  font-size: 13px;
  color: #999;
}

.metric-error {
  font-size: 13px;
  color: #cf1322;
}
</style>

<script setup lang="ts">
/**
 * 数据卡片面板：展示单个关键指标（大数字 + 描述）。
 * 可选装饰：图标（纯色柔底圆角块）、数值前缀/后缀，均遵循简约扁平规范。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'
import { resolveIcon } from '../../icons/iconRegistry'

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

const prefix = computed(() => (props.panel.options?.prefix as string) || '')
const suffix = computed(() => (props.panel.options?.suffix as string) || '')

const iconComp = computed(() => resolveIcon(props.panel.options?.icon as string | undefined))
const iconStyle = computed(() => {
  const opts = props.panel.options || {}
  return {
    color: (opts.iconColor as string) || '#1677ff',
    background: (opts.iconBg as string) || '#f0f5ff',
  }
})
</script>

<template>
  <div class="metric-panel" :class="{ 'has-icon': iconComp }">
    <div v-if="error" class="metric-error">{{ error }}</div>
    <template v-else>
      <span v-if="iconComp" class="metric-icon" :style="iconStyle">
        <component :is="iconComp" />
      </span>
      <div class="metric-body">
        <div class="metric-value">
          <span v-if="prefix" class="metric-affix">{{ prefix }}</span>
          <span class="metric-num">{{ valueText }}</span>
          <span v-if="suffix" class="metric-affix metric-suffix">{{ suffix }}</span>
        </div>
        <div v-if="labelText" class="metric-label">{{ labelText }}</div>
      </div>
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

/* 有图标时改为图标在左、内容在右的经典指标卡布局 */
.metric-panel.has-icon {
  flex-direction: row;
  align-items: center;
  gap: 14px;
}

.metric-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  font-size: 24px;
}

.metric-body {
  min-width: 0;
}

.metric-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-size: var(--dash-text-size, 32px);
  font-weight: var(--dash-text-weight, 600);
  color: var(--dash-text-color, #1a1a1a);
  line-height: 1.2;
}

.metric-num {
  word-break: break-all;
}

.metric-affix {
  font-size: 0.5em;
  font-weight: 500;
  color: #8c8c8c;
}

.metric-suffix {
  margin-left: 2px;
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

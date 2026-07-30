<script setup lang="ts">
/**
 * 全局筛选栏：按 DSL filters 渲染日期范围/下拉/文本控件，值变化联动刷新面板。
 *
 * @author huxuehao
 */
import type { DashboardFilter } from '@/types/dashboard'
import type { FilterValues } from './filterParams'

const props = defineProps<{
  filters: DashboardFilter[]
  modelValue: FilterValues
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: FilterValues): void }>()

function setVal(id: string, v: unknown) {
  emit('update:modelValue', { ...props.modelValue, [id]: v })
}
</script>

<template>
  <div v-if="filters.length" class="filter-bar">
    <div v-for="f in filters" :key="f.id" class="filter-item">
      <span class="filter-label">{{ f.label }}</span>
      <a-range-picker
        v-if="f.type === 'dateRange'"
        :value="modelValue[f.id]"
        size="small"
        @update:value="setVal(f.id, $event)"
      />
      <a-select
        v-else-if="f.type === 'select'"
        :value="modelValue[f.id]"
        :options="f.options"
        size="small"
        allow-clear
        style="min-width: 140px"
        :placeholder="f.label"
        @update:value="setVal(f.id, $event)"
      />
      <a-input
        v-else
        :value="modelValue[f.id]"
        size="small"
        allow-clear
        style="width: 160px"
        :placeholder="f.label"
        @update:value="setVal(f.id, $event)"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: #595959;
  white-space: nowrap;
}
</style>

<script setup lang="ts">
/**
 * 筛选栏：按 filters 渲染日期/下拉/文本等控件，值变化联动刷新面板。
 * 文本类型为避免逐键触发取数，本地草稿缓存，点击搜索按钮/回车/清空时才提交。
 *
 * @author huxuehao
 */
import { ref, watch } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import type { DashboardFilter } from '@/types/dashboard'
import type { FilterValues } from './filterParams'

const props = withDefaults(
  defineProps<{
    filters: DashboardFilter[]
    modelValue: FilterValues
    /** 控件尺寸 */
    size?: 'large' | 'middle' | 'small'
    /** 是否显示筛选项名称（整栏统一） */
    showLabel?: boolean
    /** card: 独立卡片样式；plain: 无边框背景，用于嵌入面板内 */
    variant?: 'card' | 'plain'
  }>(),
  {
    size: 'small',
    showLabel: true,
    variant: 'card',
  },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: FilterValues): void }>()

function setVal(id: string, v: unknown) {
  emit('update:modelValue', { ...props.modelValue, [id]: v })
}

// ── 文本草稿：输入不提交，搜索/回车/清空时才写回 modelValue ──
const textDraft = ref<Record<string, string>>({})

watch(
  () => props.modelValue,
  (mv) => {
    props.filters.forEach((f) => {
      if (f.type === 'text') {
        textDraft.value[f.id] = (mv[f.id] as string) ?? ''
      }
    })
  },
  { immediate: true, deep: true },
)

function commitText(id: string) {
  const v = textDraft.value[id] ?? ''
  if (v === ((props.modelValue[id] as string) ?? '')) return
  setVal(id, v)
}

/** 清空时立即提交，免去再点一次搜索 */
function onTextChange(id: string) {
  if (!textDraft.value[id]) {
    commitText(id)
  }
}
</script>

<template>
  <div v-if="filters.length" class="filter-bar" :class="variant">
    <div v-for="f in filters" :key="f.id" class="filter-item">
      <span v-if="showLabel" class="filter-label">{{ f.label }}</span>
      <a-range-picker
        v-if="f.type === 'dateRange'"
        :value="modelValue[f.id]"
        :size="size"
        @update:value="setVal(f.id, $event)"
      />
      <a-date-picker
        v-else-if="f.type === 'date' || f.type === 'month' || f.type === 'year'"
        :value="modelValue[f.id]"
        :picker="f.type === 'date' ? 'date' : f.type"
        :size="size"
        allow-clear
        :placeholder="f.label"
        @update:value="setVal(f.id, $event)"
      />
      <a-select
        v-else-if="f.type === 'select'"
        :value="modelValue[f.id]"
        :options="f.options"
        :size="size"
        allow-clear
        style="min-width: 140px"
        :placeholder="f.label"
        @update:value="setVal(f.id, $event)"
      />
      <a-input
        v-else
        v-model:value="textDraft[f.id]"
        :size="size"
        allow-clear
        style="width: 180px"
        :placeholder="f.label"
        @press-enter="commitText(f.id)"
        @change="onTextChange(f.id)"
      >
        <template #suffix>
          <SearchOutlined class="filter-search-icon" @click="commitText(f.id)" />
        </template>
      </a-input>
    </div>
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
}

.filter-bar.card {
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.filter-bar.plain {
  gap: 8px 16px;
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

.filter-search-icon {
  color: #bfbfbf;
  cursor: pointer;
  transition: color 0.2s ease;

  &:hover {
    color: #1677ff;
  }
}
</style>

<script setup lang="ts">
/**
 * 数据表格面板：以表格渲染数据集行列。
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

const columns = computed(() =>
  (props.data?.columns || []).map((c) => ({
    title: c.name,
    dataIndex: c.name,
    key: c.name,
    ellipsis: true,
  })),
)

const dataSource = computed(() =>
  (props.data?.rows || []).map((row, index) => ({ ...row, _rowKey: index })),
)

const pageSize = computed(() => (props.panel.options?.pageSize as number) || 10)
const pagination = computed(() =>
  dataSource.value.length > pageSize.value ? { pageSize: pageSize.value, size: 'small' as const } : false,
)

// 样式配置
const showHeader = computed(() => props.panel.options?.showHeader !== false)
const bordered = computed(() => props.panel.options?.bordered === true)
const tableSize = computed(
  () => (props.panel.options?.size as 'small' | 'middle' | 'large') || 'small',
)
const zebra = computed(() => props.panel.options?.zebra === true)

function rowClassName(_record: Record<string, unknown>, index: number): string {
  return zebra.value && index % 2 === 1 ? 'zebra-row' : ''
}
</script>

<template>
  <div class="table-panel">
    <div v-if="error" class="table-error">{{ error }}</div>
    <a-table
      v-else
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :show-header="showHeader"
      :bordered="bordered"
      :size="tableSize"
      :row-class-name="rowClassName"
      row-key="_rowKey"
    />
  </div>
</template>

<style scoped lang="scss">
.table-panel {
  height: 100%;
  overflow: auto;
}

.table-error {
  font-size: 13px;
  color: #cf1322;
}

// 自实现斑马线：奇数行背景
.table-panel :deep(.zebra-row) > td {
  background: #fafafa;
}
</style>

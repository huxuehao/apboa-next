<script setup lang="ts">
/**
 * 成本中心-会话账单：按会话聚合的分页表，点击行钻取逐轮明细。
 * token 与金额为该会话全部轮次（含废弃分支）合计。
 *
 * @author huxuehao
 */
import { ref, watch } from 'vue'
import * as costApi from '@/api/cost'
import type { CostSessionBillRow } from '@/types'
import { formatCny, formatTokens } from '../costFormat'

const props = defineProps<{
  startDate: string
  endDate: string
  agentId?: string
}>()

const emit = defineEmits<{
  openDetail: [sessionId: string]
}>()

const loading = ref(false)
const rows = ref<CostSessionBillRow[]>([])
const current = ref(1)
const size = ref(10)
const total = ref(0)
const orderBy = ref<'cost' | 'time'>('cost')

const orderOptions = [
  { label: '按成本', value: 'cost' },
  { label: '按最后活跃', value: 'time' }
]

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.pageSessions({
      current: current.value,
      size: size.value,
      startDate: props.startDate,
      endDate: props.endDate,
      agentId: props.agentId,
      orderBy: orderBy.value
    })
    const page = res.data.data
    rows.value = page?.records || []
    total.value = Number(page?.total || 0)
  } catch (e) {
    console.error('加载会话账单失败:', e)
  } finally {
    loading.value = false
  }
}

/** 筛选变化回第一页 */
watch(() => [props.startDate, props.endDate, props.agentId, orderBy.value], () => {
  current.value = 1
  loadData()
}, { immediate: true })

watch([current, size], loadData)

const columns = [
  { title: '会话', dataIndex: 'title', key: 'title', width: 220, ellipsis: true },
  { title: '智能体', dataIndex: 'agentName', key: 'agentName', width: 130, ellipsis: true },
  { title: '用户', dataIndex: 'userName', key: 'userName', width: 100, ellipsis: true },
  { title: '轮次', key: 'runCount', align: 'right' as const, width: 64 },
  { title: '用过的模型', key: 'models', width: 180 },
  { title: '输入 tok', key: 'inputTokens', align: 'right' as const, width: 88 },
  { title: '输出 tok', key: 'outputTokens', align: 'right' as const, width: 88 },
  { title: '成本', key: 'cost', align: 'right' as const, width: 96 },
  { title: '最后活跃', dataIndex: 'lastActiveAt', key: 'lastActiveAt', width: 150 }
]
</script>

<template>
  <div class="cost-bills">
    <div class="bills-toolbar flex items-center">
      <span class="bills-hint">每个会话一行，点击行查看逐轮明细；金额含废弃分支（被重新生成顶替的轮次）。</span>
      <div class="flex-1"></div>
      <ASegmented v-model:value="orderBy" :options="orderOptions" />
    </div>

    <ATable
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="sessionId"
      size="middle"
      :scroll="{ x: 1120 }"
      :pagination="{
        current,
        pageSize: size,
        total,
        showSizeChanger: true,
        showTotal: (t: number) => `共 ${t} 个会话`
      }"
      :custom-row="(record: CostSessionBillRow) => ({
        onClick: () => emit('openDetail', record.sessionId),
        style: { cursor: 'pointer' }
      })"
      @change="(p: { current?: number; pageSize?: number }) => { current = p.current || 1; size = p.pageSize || 10 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <span class="bill-title">{{ record.title || '（无标题）' }}</span>
        </template>
        <template v-else-if="column.key === 'runCount'">
          <span class="num">{{ record.runCount }}</span>
          <ATooltip v-if="Number(record.discardedRuns) > 0" title="其中被「重新生成」顶替的废弃轮次（真实产生过费用），明细页可查看每一轮">
            <span class="discarded-hint">(废{{ record.discardedRuns }})</span>
          </ATooltip>
        </template>
        <template v-else-if="column.key === 'models'">
          <ATag v-for="m in String(record.models || '').split(',').filter(Boolean)" :key="m" color="blue" :bordered="false">
            {{ m }}
          </ATag>
        </template>
        <template v-else-if="column.key === 'inputTokens'">
          <span class="num">{{ formatTokens(record.inputTokens) }}</span>
        </template>
        <template v-else-if="column.key === 'outputTokens'">
          <span class="num">{{ formatTokens(record.outputTokens) }}</span>
        </template>
        <template v-else-if="column.key === 'cost'">
          <ATooltip v-if="Number(record.unpricedRuns) > 0" :title="`有 ${record.unpricedRuns} 轮未配价未计入`">
            <span class="money num">{{ formatCny(record.cost) }} *</span>
          </ATooltip>
          <span v-else class="money num">{{ formatCny(record.cost) }}</span>
        </template>
      </template>
    </ATable>
  </div>
</template>

<style scoped lang="scss">
.cost-bills {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);

  /* 全局 .ant-tag 有 max-width:120px 截断，模型名（如 qwen3.6-35B-A3B xx）
     必须完整可读，此处放开；多 tag 超出列宽自动换行 */
  :deep(.ant-table-cell .ant-tag) {
    max-width: none;
    white-space: normal;
    height: auto;
    margin-bottom: 2px;
  }
}

.bills-toolbar {
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);

  .bills-hint {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
  }
}

.bill-title {
  color: var(--color-primary);
}

.discarded-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
  margin-left: 2px;
}

.num {
  font-variant-numeric: tabular-nums;
}

.money {
  color: #d4380d;
}
</style>

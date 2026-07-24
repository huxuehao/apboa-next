<script setup lang="ts">
/**
 * 成本中心-会话账单：按会话聚合的分页表，点击行钻取逐轮明细。
 * token 与金额为该会话全部轮次（含废弃分支）合计。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'
import { RightOutlined } from '@ant-design/icons-vue'
import * as costApi from '@/api/cost'
import type { CostSessionBillRow } from '@/types'
import { formatCny, formatTokens } from '../costFormat'

const props = defineProps<{
  startDate: string
  endDate: string
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
  { label: '成本优先', value: 'cost' },
  { label: '最近活跃', value: 'time' }
]

const pageCost = computed(() => rows.value.reduce((sum, row) => sum + Number(row.cost || 0), 0))
const pageTokens = computed(() => rows.value.reduce(
  (sum, row) => sum + Number(row.inputTokens || 0) + Number(row.outputTokens || 0),
  0,
))
const pageRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.runCount || 0), 0))
const pageVisibleReplies = computed(() => rows.value.reduce((sum, row) => sum + Number(row.visibleReplyCount || 0), 0))
const pageInternalRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.internalRunCount || 0), 0))
const pageSubAgentRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.subAgentRunCount || 0), 0))
const pageWorkflowRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.workflowRunCount || 0), 0))
const pageScheduledJobRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.scheduledJobRunCount || 0), 0))
const pageOtherInternalRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.otherInternalRunCount || 0), 0))
const pageLlmCalls = computed(() => rows.value.reduce((sum, row) => sum + Number(row.llmCallCount || 0), 0))
const pageDiscardedRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.discardedRuns || 0), 0))
const pageUnpricedRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.unpricedRuns || 0), 0))

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.pageSessions({
      current: current.value,
      size: size.value,
      startDate: props.startDate,
      endDate: props.endDate,
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
watch(() => [props.startDate, props.endDate, orderBy.value], () => {
  current.value = 1
  loadData()
}, { immediate: true })

watch([current, size], loadData)

function formatCount(value: number | string) {
  return Number(value || 0).toLocaleString()
}

const columns = [
  { title: '会话', key: 'session', width: 340 },
  { title: '归属', key: 'owner', width: 170 },
  { title: '执行构成', key: 'runs', align: 'right' as const, width: 220 },
  { title: '使用模型', key: 'models', width: 240 },
  { title: 'Token', key: 'tokens', align: 'right' as const, width: 160 },
  { title: '实际成本', key: 'cost', align: 'right' as const, width: 125 },
  { title: '最近活跃', key: 'lastActiveAt', width: 165 },
  { title: '', key: 'open', width: 44 }
]
</script>

<template>
  <div class="cost-bills">
    <div class="bills-summary">
      <div class="summary-item primary">
        <div class="summary-label">范围内会话</div>
        <div class="summary-value">{{ formatCount(total) }}<span class="summary-unit">个</span></div>
        <div class="summary-note">{{ startDate }} ~ {{ endDate }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">本页成本</div>
        <div class="summary-value money">{{ formatCny(pageCost) }}</div>
        <div class="summary-note">当前第 {{ current }} 页，共 {{ rows.length }} 个会话</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">本页 Token</div>
        <div class="summary-value">{{ formatTokens(pageTokens) }}</div>
        <div class="summary-note">包含输入与输出</div>
      </div>
      <div class="summary-item" :class="{ warning: pageDiscardedRuns > 0 || pageUnpricedRuns > 0 }">
        <div class="summary-label">本页智能体运行</div>
        <div class="summary-value">{{ formatCount(pageRuns) }}<span class="summary-unit">次</span></div>
        <div class="summary-note">
          可见回复 {{ formatCount(pageVisibleReplies) }} · 内部执行 {{ formatCount(pageInternalRuns) }} · LLM {{ formatCount(pageLlmCalls) }} 次
          <template v-if="pageInternalRuns > 0">
            <br>
            <span v-if="pageSubAgentRuns > 0">子智能体 {{ formatCount(pageSubAgentRuns) }}</span>
            <span v-if="pageSubAgentRuns > 0 && (pageWorkflowRuns > 0 || pageScheduledJobRuns > 0 || pageOtherInternalRuns > 0)"> · </span>
            <span v-if="pageWorkflowRuns > 0">工作流 {{ formatCount(pageWorkflowRuns) }}</span>
            <span v-if="pageWorkflowRuns > 0 && (pageScheduledJobRuns > 0 || pageOtherInternalRuns > 0)"> · </span>
            <span v-if="pageScheduledJobRuns > 0">定时任务 {{ formatCount(pageScheduledJobRuns) }}</span>
            <span v-if="pageScheduledJobRuns > 0 && pageOtherInternalRuns > 0"> · </span>
            <span v-if="pageOtherInternalRuns > 0">其他 {{ formatCount(pageOtherInternalRuns) }}</span>
          </template>
          <template v-if="pageDiscardedRuns > 0 || pageUnpricedRuns > 0">
            <br>
            <span v-if="pageDiscardedRuns > 0">真实废弃 {{ formatCount(pageDiscardedRuns) }}</span>
            <span v-if="pageDiscardedRuns > 0 && pageUnpricedRuns > 0"> · </span>
            <span v-if="pageUnpricedRuns > 0">未配价 {{ formatCount(pageUnpricedRuns) }}</span>
          </template>
        </div>
      </div>
    </div>

    <div class="list-panel">
      <div class="bills-toolbar">
        <div class="toolbar-copy">
          <div class="toolbar-title">会话消耗明细</div>
          <div class="toolbar-hint">每行汇总一个会话的实际模型消耗，点击可查看可见回复、分类内部执行与真实废弃分支。</div>
        </div>
        <ASegmented v-model:value="orderBy" :options="orderOptions" />
      </div>

      <div class="scope-note">
        <span class="scope-mark">计费口径</span>
        总成本包含主智能体回复、子智能体和工作流等内部执行，并按类型分别展示；只有存在消息但已不在当前链的回复才算重新生成废弃。未配价运行只统计 Token，不计入金额。
      </div>

      <ATable
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="sessionId"
        size="small"
        table-layout="fixed"
        :scroll="{ x: 1465 }"
        :pagination="{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 个会话`
        }"
        :custom-row="(record: CostSessionBillRow) => ({
          onClick: () => emit('openDetail', record.sessionId),
          class: 'clickable-row'
        })"
        @change="(p: { current?: number; pageSize?: number }) => { current = p.current || 1; size = p.pageSize || 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'session'">
            <div class="session-cell">
              <span class="bill-title" :title="record.title || '（无标题）'">{{ record.title || '（无标题）' }}</span>
              <span class="session-id">ID {{ record.sessionId }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'owner'">
            <div class="owner-cell">
              <span class="owner-agent">{{ record.agentName || '未知智能体' }}</span>
              <span class="owner-user">{{ record.userName || '未知用户' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'runs'">
            <div class="metric-stack align-right">
              <span class="num metric-main">{{ formatCount(record.runCount) }} 次运行</span>
              <span class="metric-sub">
                {{ formatCount(record.visibleReplyCount) }} 回复 · {{ formatCount(record.internalRunCount) }} 内部执行
              </span>
              <span v-if="Number(record.internalRunCount) > 0" class="run-type-tags">
                <ATag v-if="Number(record.subAgentRunCount) > 0" color="purple" :bordered="false">
                  子智能体 {{ formatCount(record.subAgentRunCount) }}
                </ATag>
                <ATag v-if="Number(record.workflowRunCount) > 0" color="blue" :bordered="false">
                  工作流 {{ formatCount(record.workflowRunCount) }}
                </ATag>
                <ATag v-if="Number(record.scheduledJobRunCount) > 0" color="gold" :bordered="false">
                  定时任务 {{ formatCount(record.scheduledJobRunCount) }}
                </ATag>
                <ATag v-if="Number(record.otherInternalRunCount) > 0" :bordered="false">
                  其他 {{ formatCount(record.otherInternalRunCount) }}
                </ATag>
              </span>
              <span class="metric-sub">{{ formatCount(record.llmCallCount) }} 次 LLM</span>
              <ATooltip v-if="Number(record.discardedRuns) > 0" title="被重新生成顶替，但真实产生过费用">
                <span class="metric-warning">真实废弃 {{ formatCount(record.discardedRuns) }}</span>
              </ATooltip>
            </div>
          </template>
          <template v-else-if="column.key === 'models'">
            <div class="model-tags">
              <ATooltip v-for="m in String(record.models || '').split(',').filter(Boolean)" :key="m" :title="m">
                <ATag color="blue" :bordered="false">{{ m }}</ATag>
              </ATooltip>
              <span v-if="!record.models" class="muted">—</span>
            </div>
          </template>
          <template v-else-if="column.key === 'tokens'">
            <div class="metric-stack align-right token-cell">
              <span class="num metric-main">{{ formatTokens(Number(record.inputTokens) + Number(record.outputTokens)) }}</span>
              <span class="metric-sub">入 {{ formatTokens(record.inputTokens) }} / 出 {{ formatTokens(record.outputTokens) }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'cost'">
            <div class="metric-stack align-right">
              <span class="money num metric-main">{{ formatCny(record.cost) }}</span>
              <ATooltip v-if="Number(record.unpricedRuns) > 0" :title="`有 ${record.unpricedRuns} 轮未配价未计入`">
                <span class="metric-warning">{{ record.unpricedRuns }} 轮未计价</span>
              </ATooltip>
              <span v-else class="metric-sub">已计价</span>
            </div>
          </template>
          <template v-else-if="column.key === 'lastActiveAt'">
            <span class="last-active num">{{ record.lastActiveAt }}</span>
          </template>
          <template v-else-if="column.key === 'open'">
            <RightOutlined class="open-icon" />
          </template>
        </template>
      </ATable>
    </div>
  </div>
</template>

<style scoped lang="scss">
.cost-bills {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.bills-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.summary-item {
  position: relative;
  overflow: hidden;
  min-width: 0;
  padding: var(--spacing-md) var(--spacing-lg);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  background: var(--color-bg-white);

  &::before {
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    content: '';
    background: var(--color-border-base);
  }

  &.primary::before { background: var(--color-primary); }
  &.warning::before { background: #fa8c16; }
}

.summary-label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.summary-value {
  margin-top: 2px;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.summary-unit {
  margin-left: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 400;
}

.summary-note {
  min-height: 18px;
  margin-top: 2px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.list-panel {
  min-width: 0;
  padding: var(--spacing-md) var(--spacing-lg) 0;
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  background: var(--color-bg-white);

  :deep(.ant-table-cell .ant-tag) {
    overflow: hidden;
    max-width: 220px;
    margin: 0;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.ant-table-thead > tr > th),
  :deep(.ant-table-tbody > tr > td) {
    white-space: nowrap;
  }

  :deep(.clickable-row) {
    cursor: pointer;

    td {
      transition: background-color 0.2s ease;
    }

    &:hover td {
      background: rgba(15, 116, 255, 0.045) !important;
    }
  }
}

.bills-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.toolbar-title {
  font-weight: 600;
}

.toolbar-hint {
  margin-top: 2px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.scope-note {
  margin-bottom: var(--spacing-md);
  padding: 9px 12px;
  border: 1px solid #d6e4ff;
  border-radius: var(--border-radius-md);
  color: var(--color-text-secondary);
  background: #f5f9ff;
  font-size: var(--font-size-xs);
}

.scope-mark {
  margin-right: 8px;
  color: var(--color-primary);
  font-weight: 600;
}

.session-cell,
.owner-cell,
.metric-stack {
  display: flex;
  flex-direction: column;
  min-width: 0;
  white-space: nowrap;
}

.bill-title {
  overflow: hidden;
  color: var(--color-primary);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-id,
.owner-user,
.metric-sub {
  margin-top: 3px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.session-id {
  overflow: hidden;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.owner-agent {
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.align-right {
  align-items: flex-end;
}

.metric-main {
  font-weight: 600;
}

.metric-warning {
  margin-top: 3px;
  color: #d46b08;
  font-size: var(--font-size-xs);
}

.run-type-tags {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;

  :deep(.ant-tag) {
    margin: 0;
    font-size: 11px;
    line-height: 19px;
  }
}

.last-active {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.open-icon {
  color: var(--color-text-placeholder);
}

.money {
  color: #d4380d;
}

.muted {
  color: var(--color-text-placeholder);
}

.num {
  font-variant-numeric: tabular-nums;
}
</style>

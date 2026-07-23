<script setup lang="ts">
/**
 * 成本中心统一执行账单：同一成本流水按根执行类型唯一归属。
 * 当前支持对话与工作流，SCHEDULED_JOB 类型契约保留供后续接入。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'
import { RightOutlined } from '@ant-design/icons-vue'
import * as costApi from '@/api/cost'
import type { CostBillType, CostExecutionBillRow } from '@/types'
import { formatCny, formatDuration, formatTokens } from '../costFormat'

const props = defineProps<{
  startDate: string
  endDate: string
}>()

const emit = defineEmits<{
  openDetail: [type: CostBillType, id: string]
}>()

const loading = ref(false)
const rows = ref<CostExecutionBillRow[]>([])
const current = ref(1)
const size = ref(10)
const total = ref(0)
const orderBy = ref<'cost' | 'time'>('cost')

const orderOptions = [
  { label: '成本优先', value: 'cost' },
  { label: '最近执行', value: 'time' }
]

const pageCost = computed(() => rows.value.reduce((sum, row) => sum + Number(row.cost || 0), 0))
const pageTokens = computed(() => rows.value.reduce(
  (sum, row) => sum + Number(row.inputTokens || 0) + Number(row.outputTokens || 0), 0,
))
const pageLlmCalls = computed(() => rows.value.reduce((sum, row) => sum + Number(row.llmCallCount || 0), 0))
const pageChatBills = computed(() => rows.value.filter(row => row.billType === 'CHAT').length)
const pageWorkflowBills = computed(() => rows.value.filter(row => row.billType === 'WORKFLOW').length)
const pageUnpricedRuns = computed(() => rows.value.reduce((sum, row) => sum + Number(row.unpricedRuns || 0), 0))

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.pageBills({
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
    console.error('加载执行账单失败:', e)
  } finally {
    loading.value = false
  }
}

watch(() => [props.startDate, props.endDate, orderBy.value], () => {
  current.value = 1
  loadData()
}, { immediate: true })

watch([current, size], loadData)

function formatCount(value: number | string | null | undefined) {
  return Number(value || 0).toLocaleString()
}

function billTypeMeta(type: CostBillType) {
  if (type === 'WORKFLOW') return { label: '工作流', color: 'blue' }
  if (type === 'SCHEDULED_JOB') return { label: '定时任务', color: 'gold' }
  return { label: '对话', color: 'purple' }
}

function statusMeta(status: string) {
  if (status === 'SUCCESS' || status === 'COMPLETED') return { label: '成功', color: 'green' }
  if (status === 'FAIL') return { label: '失败', color: 'red' }
  if (status === 'RUNNING') return { label: '运行中', color: 'processing' }
  if (status === 'HISTORICAL') return { label: '历史未关联', color: 'default' }
  return { label: status || '未知', color: 'default' }
}

function channelLabel(channel: string | null) {
  if (channel === 'STANDALONE') return '独立调试'
  if (channel === 'WEB') return '网页对话'
  if (channel === 'CHAT_KEY') return '分享对话'
  if (channel === 'SK_API') return 'API 调用'
  return '未标记入口'
}

function openBill(record: CostExecutionBillRow) {
  if (record.billType === 'SCHEDULED_JOB') return
  emit('openDetail', record.billType, record.referenceId)
}

const columns = [
  { title: '执行对象', key: 'subject', width: 350 },
  { title: '类型 / 状态', key: 'type', width: 145 },
  { title: '发起与归属', key: 'owner', width: 190 },
  { title: '执行构成', key: 'runs', align: 'right' as const, width: 210 },
  { title: '使用模型', key: 'models', width: 230 },
  { title: 'Token', key: 'tokens', align: 'right' as const, width: 160 },
  { title: '实际成本', key: 'cost', align: 'right' as const, width: 125 },
  { title: '最近执行', key: 'lastActiveAt', width: 165 },
  { title: '', key: 'open', width: 44 }
]
</script>

<template>
  <div class="cost-bills">
    <div class="bills-summary">
      <div class="summary-item primary">
        <div class="summary-label">范围内执行账单</div>
        <div class="summary-value">{{ formatCount(total) }}<span class="summary-unit">条</span></div>
        <div class="summary-note">{{ startDate }} ~ {{ endDate }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">本页实际成本</div>
        <div class="summary-value money">{{ formatCny(pageCost) }}</div>
        <div class="summary-note">每笔流水只归属一个执行类型</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">本页 Token / LLM</div>
        <div class="summary-value">{{ formatTokens(pageTokens) }}</div>
        <div class="summary-note">实际模型调用 {{ formatCount(pageLlmCalls) }} 次</div>
      </div>
      <div class="summary-item" :class="{ warning: pageUnpricedRuns > 0 }">
        <div class="summary-label">本页类型构成</div>
        <div class="summary-value compact">{{ pageChatBills }}<span class="summary-unit">对话</span> · {{ pageWorkflowBills }}<span class="summary-unit">工作流</span></div>
        <div class="summary-note">
          <span v-if="pageUnpricedRuns > 0">另有 {{ pageUnpricedRuns }} 条模型用量未配价</span>
          <span v-else>本页模型用量均已计价</span>
        </div>
      </div>
    </div>

    <div class="list-panel">
      <div class="bills-toolbar">
        <div>
          <div class="toolbar-title">执行消耗明细</div>
          <div class="toolbar-hint">对话按会话聚合，工作流按单次运行聚合；点击进入对应类型的详情。</div>
        </div>
        <ASegmented v-model:value="orderBy" :options="orderOptions" />
      </div>

      <div class="scope-note">
        <span class="scope-mark">唯一归属</span>
        对话账单包含主回复与子智能体；工作流节点成本独立归入对应工作流运行，不会再与会话账单重复。旧流水没有运行 ID 时保留为“历史未关联”。
      </div>

      <ATable
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="billId"
        size="middle"
        table-layout="fixed"
        :scroll="{ x: 1619 }"
        :pagination="{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条执行账单`
        }"
        :custom-row="(record: CostExecutionBillRow) => ({
          onClick: () => openBill(record),
          class: record.billType === 'SCHEDULED_JOB' ? '' : 'clickable-row'
        })"
        @change="(p: { current?: number; pageSize?: number }) => { current = p.current || 1; size = p.pageSize || 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'subject'">
            <div class="subject-cell">
              <span class="bill-title" :title="record.title || ''">{{ record.title || '（未命名）' }}</span>
              <span class="reference-id">
                {{ record.billType === 'CHAT' ? '会话' : '运行' }} ID {{ record.referenceId }}
              </span>
            </div>
          </template>
          <template v-else-if="column.key === 'type'">
            <div class="tag-stack">
              <ATag :color="billTypeMeta(record.billType).color" :bordered="false">
                {{ billTypeMeta(record.billType).label }}
              </ATag>
              <ATag :color="statusMeta(record.status).color" :bordered="false">
                {{ statusMeta(record.status).label }}
              </ATag>
            </div>
          </template>
          <template v-else-if="column.key === 'owner'">
            <div class="owner-cell">
              <span class="owner-main">{{ record.billType === 'CHAT' ? (record.agentName || '未知智能体') : record.userName }}</span>
              <span class="owner-sub">
                <template v-if="record.billType === 'CHAT'">用户：{{ record.userName }}</template>
                <template v-else>{{ channelLabel(record.channel) }}<span v-if="record.sessionId"> · 会话触发</span></template>
              </span>
            </div>
          </template>
          <template v-else-if="column.key === 'runs'">
            <div class="metric-stack align-right">
              <template v-if="record.billType === 'CHAT'">
                <span class="metric-main num">{{ formatCount(record.runCount) }} 次智能体运行</span>
                <span class="metric-sub">{{ formatCount(record.visibleReplyCount) }} 回复 · {{ formatCount(record.internalRunCount) }} 子智能体</span>
                <span class="metric-sub">{{ formatCount(record.llmCallCount) }} 次 LLM</span>
                <span v-if="Number(record.discardedRuns) > 0" class="metric-warning">真实废弃 {{ record.discardedRuns }}</span>
              </template>
              <template v-else>
                <span class="metric-main num">{{ record.nodeCount == null ? '—' : formatCount(record.nodeCount) }} 个流程节点</span>
                <span class="metric-sub">{{ formatCount(record.runCount) }} 个模型节点 · {{ formatCount(record.llmCallCount) }} 次 LLM</span>
                <span class="metric-sub num">运行耗时 {{ formatDuration(record.durationMs) }}</span>
              </template>
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
            <div class="metric-stack align-right">
              <span class="metric-main num">{{ formatTokens(Number(record.inputTokens) + Number(record.outputTokens)) }}</span>
              <span class="metric-sub">入 {{ formatTokens(record.inputTokens) }} / 出 {{ formatTokens(record.outputTokens) }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'cost'">
            <div class="metric-stack align-right">
              <span class="money num metric-main">{{ formatCny(record.cost) }}</span>
              <span v-if="Number(record.unpricedRuns) > 0" class="metric-warning">{{ record.unpricedRuns }} 条未计价</span>
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
.cost-bills { display: flex; flex-direction: column; gap: var(--spacing-md); }
.bills-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--spacing-md); }
.summary-item { position: relative; overflow: hidden; min-width: 0; padding: var(--spacing-md) var(--spacing-lg); border: 1px solid var(--color-border-light); border-radius: var(--border-radius-lg); background: var(--color-bg-white); }
.summary-item::before { position: absolute; inset: 0 auto 0 0; width: 3px; content: ''; background: #d9d9d9; }
.summary-item.primary::before { background: var(--color-primary); }
.summary-item.warning::before { background: #fa8c16; }
.summary-label { color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.summary-value { margin-top: 2px; font-size: 24px; font-weight: 700; font-variant-numeric: tabular-nums; }
.summary-value.compact { font-size: 21px; }
.summary-unit { margin-left: 4px; color: var(--color-text-secondary); font-size: var(--font-size-sm); font-weight: 400; }
.summary-note { min-height: 18px; margin-top: 2px; color: var(--color-text-placeholder); font-size: var(--font-size-xs); }
.list-panel { min-width: 0; padding: var(--spacing-md) var(--spacing-lg) 0; border: 1px solid var(--color-border-light); border-radius: var(--border-radius-lg); background: var(--color-bg-white); }
.list-panel :deep(.ant-table-thead > tr > th), .list-panel :deep(.ant-table-tbody > tr > td) { white-space: nowrap; }
.list-panel :deep(.clickable-row) { cursor: pointer; }
.list-panel :deep(.clickable-row:hover td) { background: rgba(15, 116, 255, 0.045) !important; }
.bills-toolbar { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); margin-bottom: var(--spacing-sm); }
.toolbar-title { font-weight: 600; }
.toolbar-hint { margin-top: 2px; color: var(--color-text-placeholder); font-size: var(--font-size-xs); }
.scope-note { margin-bottom: var(--spacing-md); padding: 9px 12px; border: 1px solid #d6e4ff; border-radius: var(--border-radius-md); color: var(--color-text-secondary); background: #f5f9ff; font-size: var(--font-size-xs); }
.scope-mark { margin-right: 8px; color: var(--color-primary); font-weight: 600; }
.subject-cell, .owner-cell, .metric-stack { display: flex; flex-direction: column; min-width: 0; }
.bill-title { overflow: hidden; color: var(--color-primary); font-weight: 600; text-overflow: ellipsis; }
.reference-id, .owner-sub, .metric-sub { margin-top: 3px; overflow: hidden; color: var(--color-text-placeholder); font-size: var(--font-size-xs); text-overflow: ellipsis; }
.owner-main { overflow: hidden; font-weight: 500; text-overflow: ellipsis; }
.tag-stack { display: flex; align-items: flex-start; flex-direction: column; gap: 5px; }
.tag-stack :deep(.ant-tag), .model-tags :deep(.ant-tag) { margin: 0; }
.model-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.model-tags :deep(.ant-tag) { overflow: hidden; max-width: 210px; text-overflow: ellipsis; }
.align-right { align-items: flex-end; }
.metric-main { font-weight: 600; }
.metric-warning { margin-top: 3px; color: #d46b08; font-size: var(--font-size-xs); }
.last-active { color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.open-icon, .muted { color: var(--color-text-placeholder); }
.money { color: #d4380d; }
.num { font-variant-numeric: tabular-nums; }
@media (max-width: 900px) { .bills-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>

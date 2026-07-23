<script setup lang="ts">
/** 工作流单次执行账单：运行状态、节点轨迹、模型用量与输入输出快照。 */
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ArrowLeftOutlined, ExportOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import * as costApi from '@/api/cost'
import type { CostWorkflowDetailVO, CostWorkflowNodeVO } from '@/types'
import { formatCny, formatDuration, formatTokens } from '../costFormat'

const props = defineProps<{ runId: string }>()
defineEmits<{ back: [] }>()

const loading = ref(false)
const data = ref<CostWorkflowDetailVO | null>(null)

const totalTokens = computed(() => Number(data.value?.inputTokens || 0) + Number(data.value?.outputTokens || 0))
const pricedModels = computed(() => (data.value?.byModel || []).map(model => ({
  ...model,
  share: Number(data.value?.totalCost || 0) > 0
    ? Number(model.cost || 0) / Number(data.value?.totalCost || 0) * 100
    : 0
})))

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.workflowDetail(props.runId)
    data.value = res.data.data
  } catch (e) {
    console.error('加载工作流账单详情失败:', e)
  } finally {
    loading.value = false
  }
}

watch(() => props.runId, loadData, { immediate: true })

function statusMeta(status: string) {
  if (status === 'SUCCESS') return { label: '执行成功', color: 'green' }
  if (status === 'FAIL') return { label: '执行失败', color: 'red' }
  if (status === 'RUNNING') return { label: '执行中', color: 'processing' }
  if (status === 'HISTORICAL') return { label: '历史未关联', color: 'default' }
  if (status === 'USAGE_ONLY') return { label: '仅有用量', color: 'orange' }
  return { label: status || '未知', color: 'default' }
}

function nodeTypeLabel(type: string) {
  const labels: Record<string, string> = { START: '开始', AGENT: '智能体', END: '结束' }
  return labels[type] || type || '未知节点'
}

function channelLabel(channel: string | null) {
  if (channel === 'STANDALONE') return '独立调试'
  if (channel === 'WEB') return '网页对话'
  if (channel === 'CHAT_KEY') return '分享对话'
  if (channel === 'SK_API') return 'API 调用'
  return '未标记入口'
}

function formatTimestamp(value: string | null) {
  return value ? dayjs(Number(value)).format('YYYY-MM-DD HH:mm:ss') : '—'
}

function formatJson(value: unknown) {
  if (value == null || value === '') return '—'
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return JSON.stringify(parsed, null, 2)
  } catch {
    return String(value)
  }
}

function formatShare(value: number) {
  return `${value >= 10 ? value.toFixed(1) : value.toFixed(2)}%`
}

const nodeColumns = [
  { title: '节点', key: 'node', width: 245 },
  { title: '状态', key: 'status', width: 105 },
  { title: '模型', key: 'models', width: 225 },
  { title: '执行 / 耗时', key: 'execution', align: 'right' as const, width: 135 },
  { title: 'Token', key: 'tokens', align: 'right' as const, width: 155 },
  { title: '实际成本', key: 'cost', align: 'right' as const, width: 115 },
  { title: '输出 / 异常', key: 'result', width: 320 }
]

const usageColumns = [
  { title: '发生时间', key: 'createdAt', width: 170 },
  { title: '智能体节点', key: 'node', width: 220 },
  { title: '模型', dataIndex: 'modelLabel', key: 'modelLabel', width: 360 },
  { title: '调用 / 耗时', key: 'execution', align: 'right' as const, width: 130 },
  { title: 'Token', key: 'tokens', align: 'right' as const, width: 155 },
  { title: '实际成本', key: 'cost', align: 'right' as const, width: 115 }
]

function nodeRowClass(record: CostWorkflowNodeVO) {
  return record.nodeType === 'AGENT' ? 'agent-node-row' : 'plain-node-row'
}
</script>

<template>
  <div class="workflow-detail">
    <button class="back-link" type="button" @click="$emit('back')">
      <ArrowLeftOutlined />
      返回执行账单
    </button>

    <ASpin :spinning="loading">
      <template v-if="data">
        <section class="detail-hero">
          <div class="hero-main">
            <div class="eyebrow">工作流执行账单</div>
            <div class="title-line">
              <h2>{{ data.workflowName || '（未命名工作流）' }}</h2>
              <ATag :color="statusMeta(data.status).color" :bordered="false">{{ statusMeta(data.status).label }}</ATag>
            </div>
            <div class="detail-meta">
              <span class="meta-pill">{{ channelLabel(data.channel) }}</span>
              <span class="meta-pill">发起人：{{ data.userName || '未记录' }}</span>
              <span v-if="data.version" class="meta-pill">版本：{{ data.version }}</span>
              <span class="meta-id">运行 ID {{ data.runId }}</span>
            </div>
          </div>
          <RouterLink v-if="data.workflowId" :to="`/workflow/${data.workflowId}`" target="_blank">
            <AButton type="primary" ghost><ExportOutlined />打开工作流</AButton>
          </RouterLink>
        </section>

        <AAlert
          v-if="data.legacy"
          type="warning"
          show-icon
          message="这是一条历史未关联工作流流水"
          description="旧版本只记录了场景和模型用量，没有保存 workflow_run.id 与节点 ID，因此只能展示当时的成本快照，无法还原完整节点轨迹。"
        />
        <AAlert v-if="data.error" type="error" show-icon message="工作流执行失败" :description="data.error" />

        <div class="summary-grid">
          <div class="stat-card primary">
            <div class="stat-label">执行总成本</div>
            <div class="stat-value money">{{ formatCny(data.totalCost) }}</div>
            <div class="stat-sub">{{ data.unpricedRunCount ? `${data.unpricedRunCount} 条用量未计价` : '模型用量均已计价' }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">总 Token</div>
            <div class="stat-value">{{ formatTokens(totalTokens) }}</div>
            <div class="stat-sub">输入 {{ formatTokens(data.inputTokens) }} · 输出 {{ formatTokens(data.outputTokens) }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">执行构成</div>
            <div class="stat-value">{{ data.nodes.length }}<span class="stat-unit">个节点</span></div>
            <div class="stat-sub">{{ data.usageRunCount }} 个模型节点 · {{ data.llmCallCount }} 次 LLM</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">工作流耗时</div>
            <div class="stat-value">{{ formatDuration(data.durationMs) }}</div>
            <div class="stat-sub">{{ formatTimestamp(data.startTime) }} 开始</div>
          </div>
        </div>

        <section class="model-panel">
          <div class="section-head">
            <div>
              <div class="section-title">模型成本构成</div>
              <div class="section-hint">按本次工作流运行内的模型用量聚合，多轮 ReAct 调用计入同一节点流水。</div>
            </div>
            <span class="section-count">共 {{ pricedModels.length }} 个模型</span>
          </div>
          <div class="model-grid">
            <div v-for="model in pricedModels" :key="model.modelLabel" class="model-card">
              <div class="model-name" :title="model.modelLabel">{{ model.modelLabel }}</div>
              <div class="model-metrics">
                <span>{{ model.runCount }} 个节点流水 · {{ model.llmCallCount }} 次 LLM</span>
                <b class="money">{{ formatCny(model.cost) }}</b>
              </div>
              <div class="model-token">入 {{ formatTokens(model.inputTokens) }} / 出 {{ formatTokens(model.outputTokens) }}</div>
              <div class="model-track"><span :style="{ width: Math.max(model.share, model.cost > 0 ? 2 : 0) + '%' }"></span></div>
              <div class="model-share">占本次成本 {{ formatShare(model.share) }}</div>
            </div>
            <AEmpty v-if="!pricedModels.length" :image-style="{ height: '44px' }" description="本次运行没有模型用量" />
          </div>
        </section>

        <section class="nodes-panel">
          <div class="section-head">
            <div>
              <div class="section-title">节点执行轨迹</div>
              <div class="section-hint">节点状态来自 workflow_node_execution；Token 与成本按节点 ID 对齐成本流水。</div>
            </div>
          </div>
          <ATable
            :columns="nodeColumns"
            :data-source="data.nodes"
            :pagination="false"
            row-key="nodeId"
            size="middle"
            :scroll="{ x: 1300 }"
            :row-class-name="nodeRowClass"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'node'">
                <div class="node-cell">
                  <span class="node-order">{{ index + 1 }}</span>
                  <div><b>{{ record.nodeName || nodeTypeLabel(record.nodeType) }}</b><span>{{ nodeTypeLabel(record.nodeType) }} · {{ record.nodeId || '未关联 ID' }}</span></div>
                </div>
              </template>
              <template v-else-if="column.key === 'status'">
                <ATag :color="statusMeta(record.status).color" :bordered="false">{{ statusMeta(record.status).label }}</ATag>
              </template>
              <template v-else-if="column.key === 'models'">
                <div class="model-tags">
                  <ATag v-for="model in record.models" :key="model" color="blue" :bordered="false">{{ model }}</ATag>
                  <span v-if="!record.models.length" class="muted">非模型节点</span>
                </div>
              </template>
              <template v-else-if="column.key === 'execution'">
                <div class="metric-stack align-right"><b>{{ record.llmCallCount }} 次 LLM</b><span>{{ formatDuration(record.durationMs) }}</span></div>
              </template>
              <template v-else-if="column.key === 'tokens'">
                <div class="metric-stack align-right"><b>{{ formatTokens(Number(record.inputTokens) + Number(record.outputTokens)) }}</b><span>入 {{ formatTokens(record.inputTokens) }} / 出 {{ formatTokens(record.outputTokens) }}</span></div>
              </template>
              <template v-else-if="column.key === 'cost'">
                <div class="metric-stack align-right"><b class="money">{{ formatCny(record.cost) }}</b><span v-if="record.unpricedRunCount" class="warning">{{ record.unpricedRunCount }} 条未计价</span></div>
              </template>
              <template v-else-if="column.key === 'result'">
                <div class="result-cell" :class="{ error: record.error }" :title="record.error || record.outputs || ''">{{ record.error || record.outputs || '—' }}</div>
              </template>
            </template>
          </ATable>
        </section>

        <section class="usage-panel">
          <div class="section-head">
            <div>
              <div class="section-title">模型用量流水</div>
              <div class="section-hint">一行对应一个智能体节点运行；“LLM 次数”反映工具选择 / ReAct 造成的多轮模型请求。</div>
            </div>
          </div>
          <ATable :columns="usageColumns" :data-source="data.usages" :pagination="false" row-key="recordId" size="middle" :scroll="{ x: 1150 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'createdAt'"><span class="num">{{ record.createdAt }}</span></template>
              <template v-else-if="column.key === 'node'"><b>{{ record.nodeName || '未关联智能体节点' }}</b></template>
              <template v-else-if="column.key === 'modelLabel'"><span class="usage-model-name">{{ record.modelLabel }}</span></template>
              <template v-else-if="column.key === 'execution'"><div class="metric-stack align-right"><b>{{ record.iterationCount }} 次 LLM</b><span>{{ formatDuration(record.durationMs) }}</span></div></template>
              <template v-else-if="column.key === 'tokens'"><div class="metric-stack align-right"><b>{{ formatTokens(Number(record.inputTokens) + Number(record.outputTokens)) }}</b><span>入 {{ formatTokens(record.inputTokens) }} / 出 {{ formatTokens(record.outputTokens) }}</span></div></template>
              <template v-else-if="column.key === 'cost'"><ATag v-if="record.cost == null" color="orange" :bordered="false">未配价</ATag><b v-else class="money">{{ formatCny(record.cost) }}</b></template>
            </template>
          </ATable>
        </section>

        <section class="io-panel">
          <div class="io-card"><div class="section-title">运行输入</div><pre>{{ formatJson(data.inputs) }}</pre></div>
          <div class="io-card"><div class="section-title">运行输出</div><pre>{{ formatJson(data.outputs) }}</pre></div>
        </section>

        <div class="scope-note">
          <InfoCircleOutlined />
          <span><b>账单口径：</b>这里仅汇总 run ID 为 {{ data.runId }} 的 WORKFLOW 流水；若由会话触发，成本仍只归入本工作流执行，不会在对话账单重复计算。</span>
        </div>
      </template>
    </ASpin>
  </div>
</template>

<style scoped lang="scss">
.workflow-detail { display: flex; flex-direction: column; gap: var(--spacing-md); }
.back-link { display: inline-flex; align-items: center; width: fit-content; padding: 0; border: 0; color: var(--color-primary); background: transparent; cursor: pointer; gap: 7px; }
.detail-hero { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-lg); padding: var(--spacing-lg); border: 1px solid #d6e4ff; border-radius: var(--border-radius-lg); background: linear-gradient(135deg, #f5f9ff 0%, var(--color-bg-white) 68%); }
.hero-main { min-width: 0; }
.eyebrow { margin-bottom: 4px; color: var(--color-primary); font-size: var(--font-size-xs); font-weight: 600; letter-spacing: .08em; }
.title-line { display: flex; align-items: center; gap: 10px; }
.title-line h2 { overflow: hidden; margin: 0; font-size: 22px; text-overflow: ellipsis; white-space: nowrap; }
.detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.meta-pill { padding: 3px 9px; border-radius: 999px; color: var(--color-text-secondary); background: var(--color-bg-light); font-size: var(--font-size-xs); }
.meta-id { color: var(--color-text-placeholder); font-size: var(--font-size-xs); font-variant-numeric: tabular-nums; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--spacing-md); }
.stat-card { position: relative; overflow: hidden; min-width: 0; padding: var(--spacing-md) var(--spacing-lg); border: 1px solid var(--color-border-light); border-radius: var(--border-radius-lg); background: var(--color-bg-white); }
.stat-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; content: ''; background: #d9d9d9; }
.stat-card.primary::before { background: var(--color-primary); }
.stat-label { color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.stat-value { margin-top: 2px; font-size: 24px; font-weight: 700; font-variant-numeric: tabular-nums; }
.stat-unit { margin-left: 4px; color: var(--color-text-secondary); font-size: var(--font-size-sm); font-weight: 400; }
.stat-sub, .section-hint, .section-count { margin-top: 2px; color: var(--color-text-placeholder); font-size: var(--font-size-xs); }
.model-panel, .nodes-panel, .usage-panel { min-width: 0; padding: var(--spacing-md) var(--spacing-lg); border: 1px solid var(--color-border-light); border-radius: var(--border-radius-lg); background: var(--color-bg-white); }
.nodes-panel, .usage-panel { padding-bottom: 0; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); margin-bottom: var(--spacing-md); }
.section-title { font-weight: 600; }
.model-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--spacing-sm); }
.model-card { min-width: 0; padding: 12px; border: 1px solid var(--color-border-light); border-radius: var(--border-radius-md); background: var(--color-bg-light); }
.model-name { overflow: hidden; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.model-metrics { display: flex; justify-content: space-between; gap: 8px; margin-top: 8px; color: var(--color-text-secondary); font-size: var(--font-size-xs); }
.model-token, .model-share { margin-top: 5px; color: var(--color-text-placeholder); font-size: var(--font-size-xs); }
.model-track { height: 6px; margin-top: 8px; overflow: hidden; border-radius: 999px; background: #e8e8e8; }
.model-track span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #0f74ff, #69b1ff); }
.node-cell { display: flex; align-items: center; gap: 9px; }
.node-order { display: inline-grid; place-items: center; flex: 0 0 auto; width: 24px; height: 24px; border-radius: 50%; color: var(--color-primary); background: #e6f4ff; font-size: 11px; font-weight: 700; }
.node-cell div { display: flex; overflow: hidden; flex-direction: column; }
.node-cell b, .node-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-cell div span { margin-top: 3px; color: var(--color-text-placeholder); font-size: var(--font-size-xs); }
.model-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.model-tags :deep(.ant-tag) { overflow: hidden; max-width: 205px; margin: 0; text-overflow: ellipsis; }
.usage-model-name { display: inline-block; max-width: 100%; padding: 3px 8px; border-radius: 4px; color: #0958d9; background: #e6f4ff; line-height: 1.45; white-space: normal; word-break: break-all; }
.metric-stack { display: flex; flex-direction: column; }
.metric-stack span { margin-top: 3px; color: var(--color-text-placeholder); font-size: var(--font-size-xs); }
.align-right { align-items: flex-end; }
.result-cell { display: -webkit-box; overflow: hidden; color: var(--color-text-secondary); white-space: normal; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.result-cell.error, .warning { color: #cf1322 !important; }
.nodes-panel :deep(.agent-node-row td) { background: rgba(22, 119, 255, .028); }
.nodes-panel :deep(.agent-node-row td:first-child) { border-left: 3px solid #1677ff; }
.io-panel { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--spacing-md); }
.io-card { min-width: 0; padding: var(--spacing-md) var(--spacing-lg); border: 1px solid var(--color-border-light); border-radius: var(--border-radius-lg); background: var(--color-bg-white); }
.io-card pre { overflow: auto; max-height: 260px; margin: 12px 0 0; padding: 12px; border-radius: var(--border-radius-md); color: var(--color-text-primary); background: #f7f8fa; font-size: 12px; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.scope-note { display: flex; align-items: flex-start; gap: 8px; padding: 11px 12px; border: 1px dashed #adc6ff; border-radius: var(--border-radius-md); color: var(--color-text-placeholder); background: #f5f9ff; font-size: var(--font-size-xs); }
.scope-note b { color: var(--color-text-secondary); }
.money { color: #d4380d; }
.muted { color: var(--color-text-placeholder); }
.num { font-variant-numeric: tabular-nums; }
@media (max-width: 900px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .model-grid, .io-panel { grid-template-columns: 1fr; } }
</style>

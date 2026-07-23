<script setup lang="ts">
/**
 * 成本中心-会话明细钻取：按「实际发生」口径展示当前回复、真实废弃与内部调用，
 * 汇总卡 + 按模型拆分 + 用量记录表格 + 口径说明。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'
import { ArrowLeftOutlined, ExportOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import * as costApi from '@/api/cost'
import type { CostSessionDetailVO, CostRunItemVO } from '@/types'
import { formatCny, formatTokens, formatDuration } from '../costFormat'

const props = defineProps<{
  sessionId: string
}>()

defineEmits<{
  back: []
}>()

const loading = ref(false)
const data = ref<CostSessionDetailVO | null>(null)

const totalTokens = computed(() => Number(data.value?.inputTokens || 0) + Number(data.value?.outputTokens || 0))
const currentReplyCount = computed(() => Math.max(
  Number(data.value?.visibleReplyCount || 0) - Number(data.value?.discardedRunCount || 0),
  0,
))
const discardedPercent = computed(() => {
  const total = Number(data.value?.totalCost || 0)
  return total > 0 ? Number(data.value?.discardedCost || 0) / total * 100 : 0
})
const averageRunCost = computed(() => {
  const runs = Number(data.value?.runCount || 0)
  return runs > 0 ? Number(data.value?.totalCost || 0) / runs : 0
})
const modelSummaries = computed(() => (data.value?.byModel || []).map(model => ({
  ...model,
  share: Number(data.value?.totalCost || 0) > 0
    ? Number(model.cost || 0) / Number(data.value?.totalCost || 0) * 100
    : 0
})))

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.sessionDetail(props.sessionId)
    data.value = res.data.data
  } catch (e) {
    console.error('加载会话明细失败:', e)
  } finally {
    loading.value = false
  }
}

watch(() => props.sessionId, loadData, { immediate: true })

function formatShare(value: number) {
  return `${value >= 10 ? value.toFixed(1) : value.toFixed(2)}%`
}

type PathStatus = CostRunItemVO['pathStatus']

function getPathStatus(record: CostRunItemVO): PathStatus {
  if (record.pathStatus) return record.pathStatus
  if (record.messageId == null) return 'INTERNAL'
  return record.onCurrentPath ? 'CURRENT' : 'DISCARDED'
}

const internalBizTypeMeta: Record<string, { label: string; color: string; description: string; rowClass: string }> = {
  SUB_AGENT: {
    label: '子智能体',
    color: 'purple',
    description: '由当前回复委派的子智能体运行，没有独立聊天回复，但真实产生 Token 和成本。',
    rowClass: 'internal-sub-agent-row'
  },
  WORKFLOW: {
    label: '工作流',
    color: 'blue',
    description: '由当前回复触发的工作流智能体节点运行，没有独立聊天回复，但真实产生 Token 和成本。',
    rowClass: 'internal-workflow-row'
  },
  SCHEDULED_JOB: {
    label: '定时任务',
    color: 'gold',
    description: '由定时任务触发的内部模型运行，没有独立聊天回复，但真实产生 Token 和成本。',
    rowClass: 'internal-scheduled-row'
  }
}

const defaultInternalMeta = {
  label: '其他内部执行',
  color: 'default',
  description: '没有独立聊天回复的内部模型运行，但真实产生 Token 和成本。',
  rowClass: 'internal-other-row'
}

function getInternalBizTypeMeta(bizType: string) {
  return internalBizTypeMeta[bizType] || defaultInternalMeta
}

const internalBreakdownText = computed(() => {
  const counts = new Map<string, number>()
  for (const run of data.value?.runs || []) {
    if (getPathStatus(run) !== 'INTERNAL') continue
    const label = getInternalBizTypeMeta(run.bizType).label
    counts.set(label, (counts.get(label) || 0) + 1)
  }
  return [...counts.entries()].map(([label, count]) => `${label} ${count}`).join(' · ')
})

const columns = [
  { title: '执行记录', key: 'round', width: 165 },
  { title: '对话 / 内部执行', key: 'content', width: 390 },
  { title: '模型', key: 'modelLabel', width: 190 },
  { title: '调用 / 耗时', key: 'execution', align: 'right' as const, width: 120 },
  { title: 'Token', key: 'tokens', align: 'right' as const, width: 140 },
  { title: '实际成本', key: 'cost', align: 'right' as const, width: 105 },
  { title: '执行类型', key: 'status', width: 110 }
]

function rowClassName(record: CostRunItemVO) {
  const status = getPathStatus(record)
  if (status === 'INTERNAL') return `internal-row ${getInternalBizTypeMeta(record.bizType).rowClass}`
  return status === 'CURRENT' ? 'current-row' : 'dead-row'
}
</script>

<template>
  <div class="cost-detail">
    <button class="back-link" type="button" @click="$emit('back')">
      <ArrowLeftOutlined />
      返回执行账单
    </button>

    <ASpin :spinning="loading">
      <template v-if="data">
        <section class="detail-hero">
          <div class="hero-main">
            <div class="eyebrow">会话成本详情</div>
            <h2 class="detail-title">{{ data.title || '（无标题）' }}</h2>
            <div class="detail-meta">
              <span class="meta-pill">智能体：{{ data.agentName || '未知' }}</span>
              <span class="meta-pill">用户：{{ data.userName || '未知' }}</span>
              <span class="meta-id">会话 ID {{ data.sessionId }}</span>
            </div>
          </div>
          <RouterLink :to="`/chat/${data.agentId}/${data.sessionId}`" target="_blank">
            <AButton type="primary" ghost>
              <ExportOutlined />
              打开原始对话
            </AButton>
          </RouterLink>
        </section>

        <div class="summary-grid">
          <div class="stat-card primary">
            <div class="stat-label">会话总成本</div>
            <div class="stat-value money">{{ formatCny(data.totalCost) }}</div>
            <div class="stat-sub">平均每次运行 {{ formatCny(averageRunCost) }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">总 Token</div>
            <div class="stat-value">{{ formatTokens(totalTokens) }}</div>
            <div class="stat-sub split">
              <span>输入 {{ formatTokens(data.inputTokens) }}</span>
              <span>输出 {{ formatTokens(data.outputTokens) }}</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-label">智能体运行</div>
            <div class="stat-value">{{ data.runCount }}<span class="stat-unit">次</span></div>
            <div class="stat-sub execution-summary">
              <span>当前回复 {{ currentReplyCount }}</span>
              <span>内部执行 {{ data.internalRunCount }}</span>
              <span>LLM {{ data.llmCallCount }} 次</span>
              <span v-if="internalBreakdownText" class="execution-types">{{ internalBreakdownText }}</span>
            </div>
          </div>
          <div class="stat-card" :class="{ danger: Number(data.discardedCost) > 0 }">
            <div class="stat-label">重新生成浪费</div>
            <div class="stat-value" :class="{ money: Number(data.discardedCost) > 0 }">{{ formatCny(data.discardedCost) }}</div>
            <div class="stat-sub">
              <span v-if="Number(data.discardedCost) > 0">
                {{ data.discardedRunCount }} 条真实废弃，占总成本 {{ formatShare(discardedPercent) }}
              </span>
              <span v-else>没有被重新生成顶替的回复</span>
            </div>
          </div>
        </div>

        <section class="model-panel">
          <div class="section-head">
            <div>
              <div class="section-title">模型成本构成</div>
              <div class="section-hint">主智能体与子智能体均计入对话成本；工作流执行在独立账单中统计，避免重复。</div>
            </div>
            <span class="model-count">共 {{ modelSummaries.length }} 个模型</span>
          </div>
          <div class="model-grid">
            <div v-for="model in modelSummaries" :key="model.modelLabel" class="model-card">
              <div class="model-name" :title="model.modelLabel">{{ model.modelLabel }}</div>
              <div class="model-metrics">
                <span><b>{{ model.runCount }}</b> 次运行</span>
                <span class="money"><b>{{ formatCny(model.cost) }}</b></span>
              </div>
              <div class="model-track">
                <span :style="{ width: Math.max(model.share, model.cost > 0 ? 2 : 0) + '%' }"></span>
              </div>
              <div class="model-share">占会话成本 {{ formatShare(model.share) }}</div>
            </div>
            <AEmpty v-if="!modelSummaries.length" :image-style="{ height: '48px' }" description="暂无模型统计" />
          </div>
        </section>

        <section class="runs-panel">
          <div class="section-head runs-head">
            <div>
              <div class="section-title">用量记录</div>
              <div class="section-hint">按实际发生时间排列；这里展示主智能体回复和由它委派的子智能体执行。</div>
            </div>
            <div class="path-legend">
              <span><i class="legend-dot current"></i>当前回复</span>
              <span><i class="legend-dot sub-agent"></i>子智能体</span>
              <span><i class="legend-dot discarded"></i>真实废弃</span>
            </div>
          </div>
          <ATable
            :columns="columns"
            :data-source="data.runs"
            :pagination="false"
            size="middle"
            row-key="recordId"
            :row-class-name="rowClassName"
            :scroll="{ x: 1220 }"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'round'">
                <div class="round-cell">
                  <span class="round-index">记录 {{ index + 1 }}</span>
                  <span class="round-time num">{{ record.createdAt }}</span>
                </div>
              </template>
              <template v-else-if="column.key === 'content'">
                <div v-if="getPathStatus(record) === 'INTERNAL'" class="internal-content">
                  <div class="internal-title">
                    <ATag :color="getInternalBizTypeMeta(record.bizType).color" :bordered="false">
                      {{ getInternalBizTypeMeta(record.bizType).label }}
                    </ATag>
                    <b>{{ record.agentName || '内部智能体' }}</b>
                  </div>
                  <span>{{ getInternalBizTypeMeta(record.bizType).description }}</span>
                </div>
                <div v-else class="content-cell">
                  <div class="content-line">
                    <span class="content-role user">问</span>
                    <span class="content-text" :title="record.userQuestion || ''">{{ record.userQuestion || '—' }}</span>
                  </div>
                  <div class="content-line">
                    <span class="content-role assistant">答</span>
                    <span class="content-text muted" :title="record.assistantSummary || ''">{{ record.assistantSummary || '—' }}</span>
                  </div>
                </div>
              </template>
              <template v-else-if="column.key === 'modelLabel'">
                <ATag color="blue" :bordered="false">{{ record.modelLabel }}</ATag>
              </template>
              <template v-else-if="column.key === 'execution'">
                <div class="metric-stack align-right">
                  <ATooltip v-if="record.iterationCount > 1" title="本轮经历了工具调用或多步推理">
                    <span class="metric-main num">{{ record.iterationCount }} 次 LLM</span>
                  </ATooltip>
                  <span v-else class="metric-main num">1 次 LLM</span>
                  <span class="metric-sub num">{{ formatDuration(record.durationMs) }}</span>
                </div>
              </template>
              <template v-else-if="column.key === 'tokens'">
                <div class="metric-stack align-right">
                  <span class="metric-main num">{{ formatTokens(Number(record.inputTokens) + Number(record.outputTokens)) }}</span>
                  <span class="metric-sub num">入 {{ formatTokens(record.inputTokens) }} / 出 {{ formatTokens(record.outputTokens) }}</span>
                </div>
              </template>
              <template v-else-if="column.key === 'cost'">
                <ATag v-if="record.cost == null" color="orange" :bordered="false">未配价</ATag>
                <span v-else class="money num metric-main">{{ formatCny(record.cost) }}</span>
              </template>
              <template v-else-if="column.key === 'status'">
                <ATag v-if="getPathStatus(record) === 'CURRENT'" color="green" :bordered="false">当前回复</ATag>
                <ATag
                  v-else-if="getPathStatus(record) === 'INTERNAL'"
                  :color="getInternalBizTypeMeta(record.bizType).color"
                  :bordered="false"
                >
                  {{ getInternalBizTypeMeta(record.bizType).label }}
                </ATag>
                <ATooltip v-else title="被重新生成顶替：界面已看不到，但真实产生了模型成本">
                  <ATag color="default" :bordered="false">已废弃</ATag>
                </ATooltip>
              </template>
            </template>
          </ATable>
          <div class="scope-note">
            <InfoCircleOutlined />
            <span>
              <b>口径说明：</b>对话账单包含主回复和子智能体；工作流成本归入对应工作流执行账单。只有存在聊天消息且已不在当前链上的回复才标记为已废弃。
            </span>
          </div>
        </section>
      </template>
    </ASpin>
  </div>
</template>

<style scoped lang="scss">
.cost-detail {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.back-link {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 0;
  border: 0;
  color: var(--color-primary);
  background: transparent;
  cursor: pointer;
  gap: 7px;
}

.detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg);
  border: 1px solid #d6e4ff;
  border-radius: var(--border-radius-lg);
  background: linear-gradient(135deg, #f5f9ff 0%, var(--color-bg-white) 68%);
}

.hero-main {
  min-width: 0;
}

.eyebrow {
  margin-bottom: 4px;
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
}

.detail-title {
  overflow: hidden;
  margin: 0;
  font-size: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.meta-pill {
  padding: 3px 9px;
  border-radius: 999px;
  color: var(--color-text-secondary);
  background: var(--color-bg-light);
  font-size: var(--font-size-xs);
}

.meta-id {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
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
    background: #d9d9d9;
  }

  &.primary::before { background: var(--color-primary); }
  &.danger::before { background: #ff4d4f; }
}

.stat-label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.stat-value {
  margin-top: 2px;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stat-unit {
  margin-left: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 400;
}

.stat-sub {
  margin-top: 2px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);

  &.split {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    flex-wrap: wrap;
  }

  &.execution-summary {
    display: flex;
    gap: 8px 12px;
    flex-wrap: wrap;
  }
}

.execution-types {
  width: 100%;
  color: var(--color-primary);
  font-weight: 500;
}

.model-panel,
.runs-panel {
  min-width: 0;
  padding: var(--spacing-md) var(--spacing-lg);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  background: var(--color-bg-white);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.section-title {
  font-weight: 600;
}

.section-hint,
.model-count {
  margin-top: 2px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.model-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-sm);

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.model-card {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-md);
  background: var(--color-bg-light);
}

.model-name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-metrics {
  display: flex;
  justify-content: space-between;
  margin-top: 9px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.model-track {
  height: 6px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e8e8e8;

  span {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, #0f74ff, #69b1ff);
  }
}

.model-share {
  margin-top: 5px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.path-legend {
  display: flex;
  gap: var(--spacing-md);
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.legend-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;

  &.current { background: #52c41a; }
  &.sub-agent { background: #722ed1; }
  &.workflow { background: #1677ff; }
  &.discarded { background: #bfbfbf; }
}

.runs-panel {
  padding-bottom: 0;

  :deep(.ant-table-cell .ant-tag) {
    height: auto;
    max-width: none;
    white-space: normal;
  }

  :deep(.dead-row) td {
    color: var(--color-text-placeholder);
    background: #fafafa !important;
  }

  :deep(.internal-sub-agent-row) td {
    background: rgba(114, 46, 209, 0.035) !important;
  }

  :deep(.internal-workflow-row) td {
    background: rgba(22, 119, 255, 0.035) !important;
  }

  :deep(.internal-scheduled-row) td {
    background: rgba(250, 173, 20, 0.05) !important;
  }

  :deep(.dead-row td:first-child) {
    border-left: 3px solid #bfbfbf;
  }

  :deep(.current-row td:first-child) {
    border-left: 3px solid #52c41a;
  }

  :deep(.internal-sub-agent-row td:first-child) {
    border-left: 3px solid #722ed1;
  }

  :deep(.internal-workflow-row td:first-child) {
    border-left: 3px solid #1677ff;
  }

  :deep(.internal-scheduled-row td:first-child) {
    border-left: 3px solid #faad14;
  }

  :deep(.internal-other-row td:first-child) {
    border-left: 3px solid #8c8c8c;
  }
}

.round-cell,
.metric-stack {
  display: flex;
  flex-direction: column;
}

.round-index,
.metric-main {
  font-weight: 600;
}

.round-time,
.metric-sub {
  margin-top: 3px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.content-cell {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.internal-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.internal-title {
  display: flex;
  align-items: center;
  gap: 7px;

  b {
    overflow: hidden;
    color: var(--color-text);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.content-line {
  display: flex;
  align-items: flex-start;
  min-width: 0;
  gap: 7px;
}

.content-role {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border-radius: 5px;
  text-align: center;
  font-size: 11px;
  line-height: 20px;

  &.user {
    color: #0958d9;
    background: #e6f4ff;
  }

  &.assistant {
    color: #531dab;
    background: #f9f0ff;
  }
}

.content-text {
  display: -webkit-box;
  overflow: hidden;
  line-height: 20px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.align-right {
  align-items: flex-end;
}

.scope-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: var(--spacing-sm);
  padding: 11px 12px;
  border-top: 1px dashed var(--color-border-light);
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);

  b {
    margin-right: 4px;
    color: var(--color-text-secondary);
  }
}

.muted {
  color: var(--color-text-secondary);
}

.money {
  color: #d4380d;
}

.num {
  font-variant-numeric: tabular-nums;
}
</style>

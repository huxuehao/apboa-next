<script setup lang="ts">
/**
 * 成本中心-概览看板：指标卡 / 未配价告警 / 成本趋势 / 模型与场景分布 / 智能体 TopN。
 * 金额为「已计价」口径（元），未配价 token 单独提示补配。
 *
 * @author huxuehao
 */
import { ref, computed, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import * as costApi from '@/api/cost'
import type { CostOverviewVO } from '@/types'
import { formatCny, formatTokens } from '../costFormat'

use([CanvasRenderer, LineChart, TooltipComponent, GridComponent, LegendComponent])

const props = defineProps<{
  startDate: string
  endDate: string
}>()

const loading = ref(false)
const data = ref<CostOverviewVO | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.overview({
      startDate: props.startDate,
      endDate: props.endDate
    })
    data.value = res.data.data
  } catch (e) {
    console.error('加载成本概览失败:', e)
  } finally {
    loading.value = false
  }
}

watch(() => [props.startDate, props.endDate], loadData, { immediate: true })

/** 补配价后重算当前区间的历史流水（未配价告警的配套动作） */
const recalcLoading = ref(false)
function confirmRecalculate() {
  Modal.confirm({
    title: '重算历史成本',
    content: `将把 ${props.startDate} ~ ${props.endDate} 内的流水按各模型「当前」单价重新计算（未配价的模型仍会跳过）。改价前的历史快照会被覆盖，确定继续？`,
    okText: '重算',
    onOk: async () => {
      recalcLoading.value = true
      try {
        const res = await costApi.recalculate({ startDate: props.startDate, endDate: props.endDate })
        message.success(`已重算 ${res.data.data} 条流水`)
        await loadData()
      } catch (e) {
        console.error('重算失败:', e)
      } finally {
        recalcLoading.value = false
      }
    }
  })
}

/** 一次性回填存量消息（模块上线前的老对话） */
const backfillLoading = ref(false)
function confirmBackfill() {
  Modal.confirm({
    title: '回填存量对话',
    content: '扫描全部聊天记录（含归档表），把成本模块上线前的老对话按消息里的 token 统计补写流水；已有流水的消息自动跳过（幂等）。老数据按模型当前单价估算。确定执行？',
    okText: '回填',
    onOk: async () => {
      backfillLoading.value = true
      try {
        const res = await costApi.backfill()
        const detail = Object.entries(res.data.data || {}).map(([t, n]) => `${t}: ${n} 条`).join('，')
        message.success(`回填完成 —— ${detail || '无新增'}`)
        await loadData()
      } catch (e) {
        console.error('回填失败:', e)
      } finally {
        backfillLoading.value = false
      }
    }
  })
}

/** 趋势图：输入/输出成本双系列面积图 */
const trendOption = computed(() => {
  const trend = data.value?.trend || []
  return {
    grid: { top: 30, right: 16, bottom: 24, left: 56 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: number) => formatCny(v)
    },
    legend: { data: ['输入成本', '输出成本'], top: 0, right: 0 },
    xAxis: {
      type: 'category',
      data: trend.map(t => t.date.slice(5)),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#d9d9d9' } },
      axisLabel: { color: '#999' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#999', formatter: (v: number) => `¥${v}` },
      splitLine: { lineStyle: { type: 'dashed' } }
    },
    series: [
      {
        name: '输入成本',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: trend.map(t => Number(t.inputCost)),
        lineStyle: { color: '#0F74FF', width: 2 },
        itemStyle: { color: '#0F74FF' },
        areaStyle: { color: 'rgba(15, 116, 255, 0.12)' }
      },
      {
        name: '输出成本',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: trend.map(t => Number(t.outputCost)),
        lineStyle: { color: '#13c2c2', width: 2 },
        itemStyle: { color: '#13c2c2' },
        areaStyle: { color: 'rgba(19, 194, 194, 0.12)' }
      }
    ]
  }
})

const totalTokens = computed(() =>
  Number(data.value?.inputTokens || 0) + Number(data.value?.outputTokens || 0),
)

const averageRunCost = computed(() => {
  const runs = Number(data.value?.runCount || 0)
  return runs > 0 ? Number(data.value?.totalCost || 0) / runs : 0
})

const workflowRunCount = computed(() =>
  Number(data.value?.byBizType?.find(item => item.bizType === 'WORKFLOW')?.runCount || 0),
)

/** 模型分布：补充成本占比、调用次数与 token 体量；未配价模型继续以斜纹提示。 */
const modelBars = computed(() => {
  const rows = data.value?.byModel || []
  return rows.map(r => {
    const share = costShare(r.cost)
    return {
      ...r,
      share,
      pct: barWidth(share, r.cost),
      totalTokens: Number(r.inputTokens) + Number(r.outputTokens),
      unpriced: Number(r.unpricedTokens) > 0 && Number(r.cost) === 0
    }
  })
})

const bizTypeMeta: Record<string, { label: string; description: string }> = {
  CHAT: { label: '对话', description: '主智能体直接生成的对话回复' },
  WORKFLOW: { label: '工作流', description: '工作流智能体节点产生的模型调用' },
  SCHEDULED_JOB: { label: '定时任务', description: '由调度任务自动触发的模型调用' },
  SUB_AGENT: { label: '子智能体', description: '主智能体委派给子智能体的模型调用' }
}

const channelMeta: Record<string, { label: string; description: string }> = {
  WEB: { label: '网页入口', description: '网页对话及其继续触发的子智能体、工作流' },
  CHAT_KEY: { label: '外嵌页面', description: '通过 Chat Key 外嵌页面进入' },
  SK_API: { label: 'API 调用', description: '通过 API Key 接口进入' },
  STANDALONE: { label: '直接运行', description: '从工作流编辑器运行或调试，不经过对话入口' },
  SCHEDULED: { label: '定时调度', description: '自动化定时任务触发的执行（智能体会话与工作流）' },
  UNKNOWN: { label: '历史未标记', description: '渠道字段上线前产生的存量流水' }
}

function getBizTypeMeta(bizType: string) {
  return bizTypeMeta[bizType] || { label: bizType, description: '其他业务场景' }
}

function getChannelMeta(channel: string | null) {
  return channelMeta[channel ?? 'UNKNOWN'] || { label: channel || '历史未标记', description: '其他入口渠道' }
}

function costShare(cost: number) {
  const total = Number(data.value?.totalCost || 0)
  return total > 0 ? Number(cost) / total * 100 : 0
}

function barWidth(share: number, cost: number) {
  return Number(cost) > 0 ? Math.max(share, 2) : 0
}

function formatShare(share: number) {
  return `${share >= 10 ? share.toFixed(1) : share.toFixed(2)}%`
}

function formatCount(value: number | string) {
  return Number(value || 0).toLocaleString()
}

function topAgentRowKey(record: CostOverviewVO['topAgents'][number]) {
  return `${record.agentId}-${record.agentName}`
}

function getSubjectType(record: CostOverviewVO['topAgents'][number]) {
  if (Number(record.agentId) !== 0) return '智能体'
  const bizTypes = String(record.bizTypes || '')
  if (bizTypes.includes('WORKFLOW')) return '工作流'
  if (bizTypes.includes('SCHEDULED_JOB')) return '定时任务'
  return '非会话任务'
}

const bizBars = computed(() => {
  const crossRows = data.value?.byBizChannel || []
  return (data.value?.byBizType || []).map(r => {
    const meta = getBizTypeMeta(r.bizType)
    const share = costShare(r.cost)
    return {
      ...r,
      ...meta,
      share,
      pct: barWidth(share, r.cost),
      totalTokens: Number(r.inputTokens) + Number(r.outputTokens),
      channelBreakdown: crossRows
        .filter(part => part.bizType === r.bizType)
        .map(part => ({
          ...part,
          key: part.channel ?? 'UNKNOWN',
          label: getChannelMeta(part.channel).label
        }))
    }
  })
})

const channelBars = computed(() => {
  const crossRows = data.value?.byBizChannel || []
  return (data.value?.byChannel || []).map(r => {
    const meta = getChannelMeta(r.channel)
    const share = costShare(r.cost)
    return {
      ...r,
      ...meta,
      key: r.channel ?? 'UNKNOWN',
      share,
      pct: barWidth(share, r.cost),
      totalTokens: Number(r.inputTokens) + Number(r.outputTokens),
      bizBreakdown: crossRows
        .filter(part => (part.channel ?? null) === (r.channel ?? null))
        .map(part => ({
          ...part,
          label: getBizTypeMeta(part.bizType).label
        }))
    }
  })
})

const topAgentColumns = [
  { title: '主体', dataIndex: 'agentName', key: 'agentName', ellipsis: true },
  { title: '类型', key: 'subjectType', width: 90 },
  { title: '调用', dataIndex: 'runCount', key: 'runCount', align: 'right' as const, width: 70 },
  { title: '关联会话', dataIndex: 'sessionCount', key: 'sessionCount', align: 'right' as const, width: 86 },
  { title: 'token（入/出）', key: 'tokens', align: 'right' as const, width: 150 },
  { title: '成本', key: 'cost', align: 'right' as const, width: 100 },
  { title: '占比', key: 'share', align: 'right' as const, width: 72 }
]
</script>

<template>
  <ASpin :spinning="loading">
    <div v-if="data" class="cost-overview">
      <!-- 未配价告警 + 维护动作 -->
      <AAlert
        v-if="Number(data.unpricedTokens) > 0"
        type="warning"
        show-icon
        class="unpriced-alert"
      >
        <template #message>
          有 {{ data.unpricedModels.length }} 个模型未配价，累计
          <b>{{ formatTokens(data.unpricedTokens) }}</b> token 未计入成本
          （{{ data.unpricedModels.map(m => m.modelLabel).join('、') }}）——
          请到「模型供应商」为其填写单价，下方所有金额都不含这部分。
        </template>
        <template #action>
          <AButton size="small" :loading="recalcLoading" @click="confirmRecalculate">补配后重算</AButton>
        </template>
      </AAlert>

      <div class="maint-row">
        <AButton size="small" type="text" :loading="backfillLoading" @click="confirmBackfill">
          回填存量对话（上线前的老记录补账，幂等）
        </AButton>
        <AButton
          v-if="Number(data.unpricedTokens) === 0"
          size="small"
          type="text"
          :loading="recalcLoading"
          @click="confirmRecalculate"
        >
          按当前价重算本区间
        </AButton>
      </div>

      <!-- 指标卡 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">总成本</div>
          <div class="stat-value money">{{ formatCny(data.totalCost) }}</div>
          <div class="stat-sub">
            {{ startDate }} ~ {{ endDate }}
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-label">总 token</div>
          <div class="stat-value">{{ formatTokens(totalTokens) }}</div>
          <div class="stat-sub stat-split">
            <span>输入 {{ formatTokens(data.inputTokens) }}</span>
            <span>输出 {{ formatTokens(data.outputTokens) }}</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-label">模型调用</div>
          <div class="stat-value">{{ formatCount(data.runCount) }}<span class="stat-unit">次</span></div>
          <div class="stat-sub stat-split">
            <span>覆盖 {{ formatCount(data.sessionCount) }} 个会话</span>
            <span>工作流 {{ formatCount(workflowRunCount) }} 次</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均单次成本</div>
          <div class="stat-value money">{{ formatCny(averageRunCost) }}</div>
          <div class="stat-sub stat-split">
            <span>输入 {{ formatCny(data.inputCost) }}</span>
            <span>输出 {{ formatCny(data.outputCost) }}</span>
          </div>
        </div>
      </div>

      <!-- 趋势 + 模型分布 -->
      <div class="chart-grid">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">成本趋势</span>
            <span class="panel-hint">元 / 天</span>
          </div>
          <VChart class="trend-chart" :option="trendOption" autoresize />
        </div>
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">按模型分布</span>
            <span class="panel-hint">成本 / 调用 / token</span>
          </div>
          <div class="model-list">
            <div v-for="m in modelBars" :key="m.modelConfigId" class="model-row">
              <div class="model-head">
                <span class="model-name" :title="m.modelLabel">{{ m.modelLabel }}</span>
                <span v-if="m.unpriced">
                  <ATag color="orange">未配价 {{ formatTokens(m.unpricedTokens) }}</ATag>
                </span>
                <span v-else class="model-value">
                  <span class="money">{{ formatCny(m.cost) }}</span>
                  <span class="share">{{ formatShare(m.share) }}</span>
                </span>
              </div>
              <div class="dimension-track">
                <span
                  class="dimension-fill model"
                  :class="{ unpriced: m.unpriced }"
                  :style="{ width: (m.unpriced ? 100 : m.pct) + '%' }"
                ></span>
              </div>
              <div class="model-meta">
                <span>{{ formatCount(m.runCount) }} 次调用</span>
                <span>{{ formatTokens(m.totalTokens) }} token</span>
                <span>输入 {{ formatTokens(m.inputTokens) }} / 输出 {{ formatTokens(m.outputTokens) }}</span>
              </div>
            </div>
            <AEmpty v-if="!modelBars.length" :image-style="{ height: '48px' }" description="暂无数据" />
          </div>
        </div>
      </div>

      <!-- 业务场景与入口渠道是两套交叉口径，保持并列展示 -->
      <div class="chart-grid bottom dimension-grid">
        <div class="panel scene-panel">
          <div class="panel-head">
            <span class="panel-title">按业务场景</span>
            <span class="panel-hint">成本花在哪类能力</span>
          </div>

          <div class="dimension-list">
            <div v-for="b in bizBars" :key="b.bizType" class="dimension-row">
              <div class="dimension-head">
                <div class="dimension-title-wrap">
                  <span class="dimension-title">{{ b.label }}</span>
                  <span class="dimension-description">{{ b.description }}</span>
                </div>
                <div class="dimension-value">
                  <span class="money">{{ formatCny(b.cost) }}</span>
                  <span class="share">{{ formatShare(b.share) }}</span>
                </div>
              </div>
              <div class="dimension-track">
                <span class="dimension-fill scene" :style="{ width: b.pct + '%' }"></span>
              </div>
              <div class="dimension-meta">
                <span>{{ formatCount(b.runCount) }} 次调用 · {{ formatTokens(b.totalTokens) }} token</span>
                <span v-if="b.channelBreakdown.length" class="breakdown">
                  入口：
                  <span v-for="part in b.channelBreakdown" :key="part.key" class="breakdown-chip">
                    {{ part.label }} {{ formatCny(part.cost) }}
                  </span>
                </span>
              </div>
            </div>
            <AEmpty v-if="!bizBars.length" :image-style="{ height: '48px' }" description="暂无数据" />
          </div>
        </div>
        <div class="panel channel-panel">
          <div class="panel-head">
            <span class="panel-title">按入口渠道</span>
            <span class="panel-hint">请求从哪里进入</span>
          </div>
          <div class="channel-grid">
            <div v-for="c in channelBars" :key="c.key" class="channel-card">
              <div class="channel-card-head">
                <span class="channel-name">{{ c.label }}</span>
                <span class="share">{{ formatShare(c.share) }}</span>
              </div>
              <div class="channel-description">{{ c.description }}</div>
              <div class="channel-cost money">{{ formatCny(c.cost) }}</div>
              <div class="dimension-track">
                <span class="dimension-fill channel" :style="{ width: c.pct + '%' }"></span>
              </div>
              <div class="channel-metrics">
                {{ formatCount(c.runCount) }} 次调用 · {{ formatTokens(c.totalTokens) }} token
              </div>
              <div v-if="c.bizBreakdown.length" class="channel-breakdown">
                <span v-for="part in c.bizBreakdown" :key="part.bizType" class="breakdown-chip">
                  {{ part.label }} {{ formatCny(part.cost) }}
                </span>
              </div>
            </div>
            <AEmpty v-if="!channelBars.length" :image-style="{ height: '48px' }" description="暂无数据" />
          </div>
          <div class="dimension-note">
            <span class="note-mark">口径说明</span>
            业务场景解释“花在哪里”，入口渠道解释“从哪里触发”，两组金额不要相加。
          </div>
        </div>
      </div>

      <!-- 消耗主体全宽展示，避免长工作流名称与多列指标相互挤压 -->
      <div class="panel subject-panel">
        <div class="panel-head">
          <span class="panel-title">消耗主体 Top10</span>
          <span class="panel-hint">按成本降序，调用与关联会话分开统计</span>
        </div>
        <ATable
          :columns="topAgentColumns"
          :data-source="data.topAgents"
          :pagination="false"
          size="small"
          :row-key="topAgentRowKey"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'subjectType'">
              <ATag :color="getSubjectType(record) === '智能体' ? 'blue' : 'purple'" :bordered="false">
                {{ getSubjectType(record) }}
              </ATag>
            </template>
            <template v-else-if="column.key === 'tokens'">
              <span class="num">{{ formatTokens(record.inputTokens) }} / {{ formatTokens(record.outputTokens) }}</span>
            </template>
            <template v-else-if="column.key === 'sessionCount'">
              <span class="num">{{ Number(record.sessionCount) > 0 ? formatCount(record.sessionCount) : '—' }}</span>
            </template>
            <template v-else-if="column.key === 'cost'">
              <span class="money num">{{ formatCny(record.cost) }}</span>
            </template>
            <template v-else-if="column.key === 'share'">
              <span class="num share">{{ formatShare(costShare(record.cost)) }}</span>
            </template>
          </template>
        </ATable>
      </div>
    </div>
  </ASpin>
</template>

<style scoped lang="scss">
.cost-overview {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.unpriced-alert {
  b { font-variant-numeric: tabular-nums; }
}

.maint-row {
  display: flex;
  gap: var(--spacing-sm);

  :deep(.ant-btn-text) {
    color: var(--color-text-placeholder);
    font-size: var(--font-size-xs);

    &:hover {
      color: var(--color-primary);
    }
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);

  .stat-label {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
  }

  .stat-value {
    font-size: 24px;
    font-weight: 700;
    margin-top: 2px;
    font-variant-numeric: tabular-nums;

    .stat-unit {
      font-size: var(--font-size-sm);
      font-weight: 400;
      color: var(--color-text-secondary);
      margin-left: 4px;
    }
  }

  .stat-sub {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
    margin-top: 2px;
    font-variant-numeric: tabular-nums;

    &.stat-split {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      flex-wrap: wrap;
    }
  }
}

.money {
  color: #d4380d;
}

.num {
  font-variant-numeric: tabular-nums;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: var(--spacing-md);

  &.bottom {
    grid-template-columns: 1fr 1fr;
  }

  @media (max-width: 900px) {
    grid-template-columns: 1fr !important;
  }
}

.panel {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);
  min-width: 0;

  .panel-head {
    display: flex;
    align-items: baseline;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-sm);

    .panel-title {
      font-weight: 600;
    }

    .panel-hint {
      font-size: var(--font-size-xs);
      color: var(--color-text-placeholder);
    }
  }
}

.trend-chart {
  height: 260px;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.model-row {
  padding: 9px 0;
  border-bottom: 1px solid var(--color-border-light);

  &:last-child {
    border-bottom: 0;
  }
}

.model-head,
.model-value,
.model-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-head {
  justify-content: space-between;
}

.model-name {
  min-width: 0;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-value {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.model-meta {
  margin-top: 7px;
  justify-content: space-between;
  flex-wrap: wrap;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.subject-panel {
  :deep(.ant-table-cell .ant-tag) {
    max-width: none;
  }
}

.dimension-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dimension-row {
  padding: 9px 0;
  border-bottom: 1px solid var(--color-border-light);

  &:last-child {
    border-bottom: 0;
  }
}

.dimension-head,
.channel-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.dimension-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.dimension-title,
.channel-name {
  font-weight: 600;
  white-space: nowrap;
}

.dimension-description,
.channel-description,
.channel-metrics {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.dimension-value {
  display: flex;
  align-items: baseline;
  gap: 8px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.share {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.dimension-track {
  height: 8px;
  margin-top: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-light);
}

.dimension-fill {
  display: block;
  height: 100%;
  border-radius: inherit;

  &.scene {
    background: linear-gradient(90deg, #722ed1, #b37feb);
  }

  &.channel {
    background: linear-gradient(90deg, #0f74ff, #69b1ff);
  }

  &.model {
    background: linear-gradient(90deg, #0f74ff, #69b1ff);

    &.unpriced {
      background: repeating-linear-gradient(45deg, #ffd591, #ffd591 4px, transparent 4px, transparent 8px);
    }
  }
}

.dimension-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 7px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.breakdown {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  flex-wrap: wrap;
}

.breakdown-chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border: 1px solid #d9d9d9;
  border-radius: 999px;
  background: var(--color-bg-white);
  color: var(--color-text-secondary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.channel-panel {
  .panel-head {
    margin-bottom: var(--spacing-md);
  }
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: var(--spacing-sm);
}

.channel-card {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-md);
  background: var(--color-bg-light);
}

.channel-description {
  min-height: 36px;
  margin-top: 3px;
  line-height: 18px;
}

.channel-cost {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.channel-metrics {
  margin-top: 7px;
  font-variant-numeric: tabular-nums;
}

.channel-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
}

.dimension-note {
  margin-top: var(--spacing-md);
  padding: 8px 10px;
  border-radius: var(--border-radius-sm);
  background: #f0f7ff;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);

  .note-mark {
    margin-right: 6px;
    color: #0f74ff;
    font-weight: 600;
  }
}

@media (max-width: 1100px) {
  .dimension-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .breakdown {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .dimension-title-wrap {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .channel-grid {
    grid-template-columns: 1fr;
  }
}
</style>

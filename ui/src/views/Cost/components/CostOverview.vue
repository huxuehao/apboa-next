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
  agentId?: string
}>()

const loading = ref(false)
const data = ref<CostOverviewVO | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.overview({
      startDate: props.startDate,
      endDate: props.endDate,
      agentId: props.agentId
    })
    data.value = res.data.data
  } catch (e) {
    console.error('加载成本概览失败:', e)
  } finally {
    loading.value = false
  }
}

watch(() => [props.startDate, props.endDate, props.agentId], loadData, { immediate: true })

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

/** 模型分布条：宽度按最大成本归一（未配价模型排后、灰条按 token 归一提示体量） */
const modelBars = computed(() => {
  const rows = data.value?.byModel || []
  const maxCost = Math.max(...rows.map(r => Number(r.cost)), 0.000001)
  return rows.map(r => ({
    ...r,
    pct: Math.max(Number(r.cost) / maxCost * 100, Number(r.cost) > 0 ? 2 : 0),
    unpriced: Number(r.unpricedTokens) > 0 && Number(r.cost) === 0
  }))
})

const bizTypeLabels: Record<string, string> = {
  CHAT: '对话',
  WORKFLOW: '工作流',
  SCHEDULED_JOB: '定时任务',
  SUB_AGENT: '子智能体'
}

const bizBars = computed(() => {
  const rows = data.value?.byBizType || []
  const maxCost = Math.max(...rows.map(r => Number(r.cost)), 0.000001)
  return rows.map(r => ({
    ...r,
    label: bizTypeLabels[r.bizType] || r.bizType,
    pct: Math.max(Number(r.cost) / maxCost * 100, 2)
  }))
})

const channelLabels: Record<string, string> = {
  WEB: '网页对话',
  CHAT_KEY: '外嵌页面',
  SK_API: 'API 调用'
}

const channelBars = computed(() => {
  const rows = data.value?.byChannel || []
  const maxCost = Math.max(...rows.map(r => Number(r.cost)), 0.000001)
  return rows.map(r => ({
    ...r,
    key: r.channel ?? 'UNKNOWN',
    label: r.channel ? (channelLabels[r.channel] || r.channel) : '未标记（历史）',
    pct: Math.max(Number(r.cost) / maxCost * 100, 2)
  }))
})

const topAgentColumns = [
  { title: '智能体', dataIndex: 'agentName', key: 'agentName', ellipsis: true },
  { title: '会话数', dataIndex: 'sessionCount', key: 'sessionCount', align: 'right' as const, width: 80 },
  { title: 'token（入/出）', key: 'tokens', align: 'right' as const, width: 150 },
  { title: '成本', key: 'cost', align: 'right' as const, width: 100 }
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
          <div class="stat-label">输入 token</div>
          <div class="stat-value">{{ formatTokens(data.inputTokens) }}</div>
          <div class="stat-sub">计价部分 {{ formatCny(data.inputCost) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">输出 token</div>
          <div class="stat-value">{{ formatTokens(data.outputTokens) }}</div>
          <div class="stat-sub">计价部分 {{ formatCny(data.outputCost) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">调用 / 会话</div>
          <div class="stat-value">{{ data.runCount }}<span class="stat-unit">次</span></div>
          <div class="stat-sub">覆盖 {{ data.sessionCount }} 个会话</div>
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
            <span class="panel-hint">按成本降序</span>
          </div>
          <div class="hbar-list">
            <div v-for="m in modelBars" :key="m.modelConfigId" class="hbar-row">
              <span class="hbar-name" :title="m.modelLabel">{{ m.modelLabel }}</span>
              <span class="hbar-track">
                <span class="hbar-fill" :class="{ unpriced: m.unpriced }" :style="{ width: (m.unpriced ? 100 : m.pct) + '%' }"></span>
              </span>
              <span class="hbar-val">
                <ATag v-if="m.unpriced" color="orange">未配价 {{ formatTokens(m.unpricedTokens) }}</ATag>
                <template v-else>{{ formatCny(m.cost) }}</template>
              </span>
            </div>
            <AEmpty v-if="!modelBars.length" :image-style="{ height: '48px' }" description="暂无数据" />
          </div>
        </div>
      </div>

      <!-- TopN + 场景分布 -->
      <div class="chart-grid bottom">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">智能体 Top10</span>
            <span class="panel-hint">按成本降序</span>
          </div>
          <ATable
            :columns="topAgentColumns"
            :data-source="data.topAgents"
            :pagination="false"
            size="small"
            row-key="agentId"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'tokens'">
                <span class="num">{{ formatTokens(record.inputTokens) }} / {{ formatTokens(record.outputTokens) }}</span>
              </template>
              <template v-else-if="column.key === 'cost'">
                <span class="money num">{{ formatCny(record.cost) }}</span>
              </template>
            </template>
          </ATable>
        </div>
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">按渠道 / 场景</span>
            <span class="panel-hint">钱从哪个入口烧掉</span>
          </div>
          <div class="hbar-list">
            <div v-for="b in channelBars" :key="b.key" class="hbar-row">
              <span class="hbar-name">{{ b.label }}</span>
              <span class="hbar-track"><span class="hbar-fill" :style="{ width: b.pct + '%' }"></span></span>
              <span class="hbar-val">{{ formatCny(b.cost) }}</span>
            </div>
            <AEmpty v-if="!channelBars.length" :image-style="{ height: '48px' }" description="暂无数据" />
          </div>
          <div class="biz-split">
            场景口径：
            <span v-for="b in bizBars" :key="b.bizType" class="biz-item">{{ b.label }} {{ formatCny(b.cost) }}</span>
            <span v-if="!bizBars.length">暂无数据</span>
          </div>
        </div>
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

.hbar-list {
  display: flex;
  flex-direction: column;
}

.hbar-row {
  display: grid;
  grid-template-columns: minmax(90px, 150px) 1fr 110px;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 6px 0;
  font-size: var(--font-size-sm);

  .hbar-name {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .hbar-track {
    height: 14px;
    background: var(--color-bg-light);
    border-radius: 4px;
    overflow: hidden;
  }

  .hbar-fill {
    display: block;
    height: 100%;
    border-radius: 4px;
    background: #0F74FF;

    &.unpriced {
      background: repeating-linear-gradient(45deg, #ffd591, #ffd591 4px, transparent 4px, transparent 8px);
    }
  }

  .hbar-val {
    text-align: right;
    font-variant-numeric: tabular-nums;
    color: var(--color-text-secondary);
  }
}

.biz-split {
  border-top: 1px dashed var(--color-border-light);
  margin-top: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);

  .biz-item {
    margin-right: var(--spacing-md);
    font-variant-numeric: tabular-nums;
  }
}
</style>

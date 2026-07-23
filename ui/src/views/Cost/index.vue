<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
/**
 * 成本中心容器页：概览看板 / 会话账单 两个 tab + 会话明细钻取。
 * 时间范围与智能体过滤为两 tab 共享；账单行点击进入明细（组件内切换，返回保留筛选）。
 *
 * @author huxuehao
 */
import { ref, computed, onMounted } from 'vue'
import dayjs, { Dayjs } from 'dayjs'
import * as agentApi from '@/api/agent'
import CostOverview from './components/CostOverview.vue'
import CostSessionBills from './components/CostSessionBills.vue'
import CostSessionDetail from './components/CostSessionDetail.vue'
import CostModelPricing from './components/CostModelPricing.vue'

type RangePreset = '7d' | '30d' | 'month' | 'custom'

const activeTab = ref<'overview' | 'sessions' | 'pricing'>('overview')
const rangePreset = ref<RangePreset>('30d')
const customRange = ref<[Dayjs, Dayjs] | null>(null)
const agentId = ref<string | undefined>(undefined)
/** 非空时显示会话明细钻取视图（返回后保留筛选与分页现场） */
const detailSessionId = ref<string | null>(null)

const presetOptions = [
  { label: '近 7 天', value: '7d' },
  { label: '近 30 天', value: '30d' },
  { label: '本月', value: 'month' },
  { label: '自定义', value: 'custom' }
]

/** 统一换算为 yyyy-MM-dd 闭区间，供两个 tab 的组件复用 */
const dateQuery = computed<{ startDate: string; endDate: string }>(() => {
  const today = dayjs()
  if (rangePreset.value === '7d') {
    return { startDate: today.subtract(6, 'day').format('YYYY-MM-DD'), endDate: today.format('YYYY-MM-DD') }
  }
  if (rangePreset.value === 'month') {
    return { startDate: today.startOf('month').format('YYYY-MM-DD'), endDate: today.format('YYYY-MM-DD') }
  }
  if (rangePreset.value === 'custom' && customRange.value) {
    return {
      startDate: customRange.value[0].format('YYYY-MM-DD'),
      endDate: customRange.value[1].format('YYYY-MM-DD')
    }
  }
  return { startDate: today.subtract(29, 'day').format('YYYY-MM-DD'), endDate: today.format('YYYY-MM-DD') }
})

/** 智能体过滤下拉（拉一页足量列表） */
const agentOptions = ref<{ label: string; value: string }[]>([])
onMounted(async () => {
  try {
    const res = await agentApi.page({ current: 1, size: 200 } as never)
    const records = res.data.data?.records || []
    agentOptions.value = records.map(a => ({ label: a.name, value: String(a.id) }))
  } catch {
    agentOptions.value = []
  }
})

function openDetail(sessionId: string) {
  detailSessionId.value = sessionId
}
</script>

<template>
  <div class="cost-page">
    <!-- 明细钻取视图（覆盖列表，返回保留筛选现场） -->
    <CostSessionDetail
      v-if="detailSessionId"
      :session-id="detailSessionId"
      @back="detailSessionId = null"
    />

    <template v-else>
      <div class="cost-head flex items-center">
        <ATabs v-model:activeKey="activeTab" class="cost-tabs">
          <ATabPane key="overview" tab="概览看板" />
          <ATabPane key="sessions" tab="会话账单" />
          <ATabPane key="pricing" tab="模型配价" />
        </ATabs>
        <div class="flex-1"></div>
        <template v-if="activeTab !== 'pricing'">
          <ASelect
            v-model:value="agentId"
            :options="agentOptions"
            placeholder="全部智能体"
            allow-clear
            show-search
            option-filter-prop="label"
            class="agent-filter"
          />
          <ASegmented v-model:value="rangePreset" :options="presetOptions" />
          <ARangePicker
            v-if="rangePreset === 'custom'"
            v-model:value="customRange"
            :allow-clear="false"
          />
        </template>
      </div>

      <CostOverview
        v-if="activeTab === 'overview'"
        :start-date="dateQuery.startDate"
        :end-date="dateQuery.endDate"
        :agent-id="agentId"
      />
      <CostSessionBills
        v-else-if="activeTab === 'sessions'"
        :start-date="dateQuery.startDate"
        :end-date="dateQuery.endDate"
        :agent-id="agentId"
        @open-detail="openDetail"
      />
      <CostModelPricing v-else />
    </template>
  </div>
</template>

<style scoped lang="scss">
.cost-page {
  padding: var(--spacing-lg);
  max-width: 1400px;
  margin: 0 auto;
}

.cost-head {
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;

  .cost-tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 0;
    }
  }

  .agent-filter {
    min-width: 180px;
  }
}
</style>

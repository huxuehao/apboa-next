<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
/**
 * 成本中心容器页：概览看板 / 执行账单 / 模型配价，以及按执行类型钻取详情。
 *
 * @author huxuehao
 */
import { ref, computed } from 'vue'
import dayjs, { Dayjs } from 'dayjs'
import CostOverview from './components/CostOverview.vue'
import CostExecutionBills from './components/CostExecutionBills.vue'
import CostSessionDetail from './components/CostSessionDetail.vue'
import CostWorkflowDetail from './components/CostWorkflowDetail.vue'
import CostModelPricing from './components/CostModelPricing.vue'
import type { CostBillType } from '@/types'

type RangePreset = '7d' | '30d' | 'month' | 'custom'

const activeTab = ref<'overview' | 'sessions' | 'pricing'>('overview')
const rangePreset = ref<RangePreset>('30d')
const customRange = ref<[Dayjs, Dayjs] | null>(null)
/** 非空时显示类型化账单详情（返回后保留筛选与分页现场）。 */
const detailTarget = ref<{ type: CostBillType; id: string } | null>(null)

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

function openDetail(type: CostBillType, id: string) {
  detailTarget.value = { type, id }
}
</script>

<template>
  <div class="cost-page">
    <!-- 明细钻取视图（覆盖列表，返回保留筛选现场） -->
    <CostSessionDetail
      v-if="detailTarget?.type === 'CHAT'"
      :session-id="detailTarget.id"
      @back="detailTarget = null"
    />
    <CostWorkflowDetail
      v-else-if="detailTarget?.type === 'WORKFLOW'"
      :run-id="detailTarget.id"
      @back="detailTarget = null"
    />

    <template v-else-if="!detailTarget">
      <div class="cost-head flex items-center">
        <ATabs v-model:activeKey="activeTab" class="cost-tabs">
          <ATabPane key="overview" tab="概览看板" />
          <ATabPane key="sessions" tab="执行账单" />
          <ATabPane key="pricing" tab="模型配价" />
        </ATabs>
        <div class="flex-1"></div>
        <template v-if="activeTab !== 'pricing'">
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
      />
      <CostExecutionBills
        v-else-if="activeTab === 'sessions'"
        :start-date="dateQuery.startDate"
        :end-date="dateQuery.endDate"
        @open-detail="openDetail"
      />
      <CostModelPricing v-else />
    </template>
  </div>
</template>

<style scoped lang="scss">
.cost-page {
  width: 100%;
  padding: var(--spacing-lg);
  max-width: none;
  margin: 0;
  box-sizing: border-box;
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
}
</style>

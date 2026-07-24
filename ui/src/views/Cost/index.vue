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

const tabOptions = [
  { label: '概览看板', value: 'overview' },
  { label: '执行账单', value: 'sessions' },
  { label: '模型配价', value: 'pricing' }
]

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
      <section class="intro-section">
        <h3 class="intro-title">成本中心</h3>
        <p class="intro-desc text-secondary">
          统一归集平台各执行链路的 token 用量与成本流水：从模型计价、会话/工作流/定时任务账单，到月度预算管控与明细钻取，为智能体运营提供可核对的成本视图。
        </p>
      </section>

      <section class="filter-section flex justify-between items-center">
        <div class="filter-left">
          <ASegmented v-model:value="activeTab" :options="tabOptions" />
        </div>
        <div v-if="activeTab !== 'pricing'" class="filter-right flex items-center">
          <ASegmented v-model:value="rangePreset" :options="presetOptions" />
          <ARangePicker
            v-if="rangePreset === 'custom'"
            v-model:value="customRange"
            :allow-clear="false"
          />
        </div>
      </section>

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

.intro-section {
  margin-bottom: var(--spacing-lg);

  .intro-title {
    font-size: var(--font-size-2xl);
    font-weight: 600;
    color: var(--color-text-primary);
    margin-bottom: var(--spacing-sm);
  }

  .intro-desc {
    font-size: var(--font-size-base);
    line-height: 1.6;
    max-width: 900px;
  }
}

.filter-section {
  margin-bottom: var(--spacing-md);
  gap: var(--spacing-md);
  flex-wrap: wrap;

  .filter-right {
    gap: var(--spacing-sm);
  }
}
</style>

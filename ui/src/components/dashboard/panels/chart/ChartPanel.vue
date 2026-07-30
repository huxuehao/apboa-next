<script setup lang="ts">
/**
 * 图表面板：基于 vue-echarts 渲染，option 由 chartOptionBuilder 按类型生成并支持深度覆盖。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart, RadarChart, ScatterChart } from 'echarts/charts'
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'
import { buildChartOption } from './chartOptionBuilder'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  RadarChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DatasetComponent,
])

const props = defineProps<{
  panel: PanelDsl
  data: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const option = computed(() => buildChartOption(props.panel, props.data))
</script>

<template>
  <div class="chart-panel">
    <div v-if="error" class="chart-error">{{ error }}</div>
    <v-chart v-else class="chart" :option="option" :loading="loading" autoresize />
  </div>
</template>

<style scoped lang="scss">
.chart-panel {
  height: 100%;
  width: 100%;
}

.chart {
  height: 100%;
  width: 100%;
}

.chart-error {
  font-size: 13px;
  color: #cf1322;
}
</style>

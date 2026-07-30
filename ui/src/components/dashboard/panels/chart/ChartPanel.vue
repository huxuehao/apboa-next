<script setup lang="ts">
/**
 * 图表面板：基于 vue-echarts 渲染，option 由 chartOptionBuilder 按类型生成并支持深度覆盖。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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

// 测量容器尺寸，供图表自适应（字号/间距/符号大小按尺寸档位调整）
const wrap = ref<HTMLElement | null>(null)
const size = reactive({ w: 0, h: 0 })
let ro: ResizeObserver | null = null

onMounted(() => {
  if (wrap.value) {
    ro = new ResizeObserver((entries) => {
      const cr = entries[0]?.contentRect
      if (cr) {
        size.w = cr.width
        size.h = cr.height
      }
    })
    ro.observe(wrap.value)
  }
})
onBeforeUnmount(() => ro?.disconnect())

const option = computed(() => buildChartOption(props.panel, props.data, { width: size.w, height: size.h }))
</script>

<template>
  <div ref="wrap" class="chart-panel">
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

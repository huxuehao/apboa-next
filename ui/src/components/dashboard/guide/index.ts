/**
 * 数据集使用说明统一注册入口。新增面板说明：实现一个 Guide 组件，在此注册即可。
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { registerGuide } from './guideRegistry'
import MetricDatasetGuide from './guides/MetricDatasetGuide.vue'
import KpiDatasetGuide from './guides/KpiDatasetGuide.vue'
import ProgressDatasetGuide from './guides/ProgressDatasetGuide.vue'
import TableDatasetGuide from './guides/TableDatasetGuide.vue'
import ScrollTableDatasetGuide from './guides/ScrollTableDatasetGuide.vue'
import FlipNumberDatasetGuide from './guides/FlipNumberDatasetGuide.vue'
import CartesianDatasetGuide from './guides/CartesianDatasetGuide.vue'
import PieDatasetGuide from './guides/PieDatasetGuide.vue'
import RadarDatasetGuide from './guides/RadarDatasetGuide.vue'
import FilterDatasetGuide from './guides/FilterDatasetGuide.vue'

let registered = false

/** 注册所有内置数据集说明（幂等） */
export function registerBuiltinGuides() {
  if (registered) return
  registerGuide({
    key: 'metric',
    title: '数据卡片',
    panelTypes: ['metric'],
    component: markRaw(MetricDatasetGuide),
  })
  registerGuide({
    key: 'kpi',
    title: 'KPI 趋势',
    panelTypes: ['kpi'],
    component: markRaw(KpiDatasetGuide),
  })
  registerGuide({
    key: 'progress',
    title: '进度环',
    panelTypes: ['progress'],
    component: markRaw(ProgressDatasetGuide),
  })
  registerGuide({
    key: 'flipNumber',
    title: '数字翻牌',
    panelTypes: ['flipNumber'],
    component: markRaw(FlipNumberDatasetGuide),
  })
  registerGuide({
    key: 'table',
    title: '数据表格',
    panelTypes: ['table'],
    component: markRaw(TableDatasetGuide),
  })
  registerGuide({
    key: 'scrollTable',
    title: '滚动轮播表',
    panelTypes: ['scrollTable'],
    component: markRaw(ScrollTableDatasetGuide),
  })
  registerGuide({
    key: 'cartesian',
    title: '柱状 / 折线 / 面积 / 散点图',
    panelTypes: ['bar', 'line', 'area', 'scatter'],
    component: markRaw(CartesianDatasetGuide),
  })
  registerGuide({
    key: 'pie',
    title: '饼图',
    panelTypes: ['pie'],
    component: markRaw(PieDatasetGuide),
  })
  registerGuide({
    key: 'radar',
    title: '雷达图',
    panelTypes: ['radar'],
    component: markRaw(RadarDatasetGuide),
  })
  registerGuide({
    key: 'filter',
    title: '面板私有筛选器',
    panelTypes: [],
    component: markRaw(FilterDatasetGuide),
  })
  registered = true
}

export * from './guideRegistry'

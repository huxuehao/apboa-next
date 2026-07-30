/**
 * 图表面板描述符（多种图表类型共用 ChartPanel 组件）
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import {
  AreaChartOutlined,
  BarChartOutlined,
  DotChartOutlined,
  LineChartOutlined,
  PieChartOutlined,
  RadarChartOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'
import type { PanelConfigField, PanelDefinition } from '@/types/dashboard'
import ChartPanel from './ChartPanel.vue'

const cartesianSchema: PanelConfigField[] = [
  { key: 'fieldMapping.x', label: '分类轴(X)', type: 'field', group: '数据映射' },
  { key: 'fieldMapping.y', label: '数值列(Y)', type: 'fields', group: '数据映射' },
]

const pieSchema: PanelConfigField[] = [
  { key: 'fieldMapping.name', label: '名称列', type: 'field', group: '数据映射' },
  { key: 'fieldMapping.value', label: '数值列', type: 'field', group: '数据映射' },
]

const radarSchema: PanelConfigField[] = [
  { key: 'fieldMapping.name', label: '维度列', type: 'field', group: '数据映射' },
  { key: 'fieldMapping.y', label: '数值列(系列)', type: 'fields', group: '数据映射' },
]

function chartDefinition(
  type: string,
  name: string,
  icon: Component,
  schema: PanelConfigField[],
): PanelDefinition {
  return {
    type,
    name,
    category: '图表',
    icon: markRaw(icon),
    component: markRaw(ChartPanel),
    dataRequirement: { needsDataset: true },
    defaultDsl: () => ({
      layout: { x: 0, y: 0, w: 8, h: 6 },
      options: {},
      fieldMapping: {},
    }),
    configSchema: schema,
  }
}

export const chartPanelDefinitions: PanelDefinition[] = [
  chartDefinition('line', '折线图', LineChartOutlined, cartesianSchema),
  chartDefinition('bar', '柱状图', BarChartOutlined, cartesianSchema),
  chartDefinition('area', '面积图', AreaChartOutlined, cartesianSchema),
  chartDefinition('scatter', '散点图', DotChartOutlined, cartesianSchema),
  chartDefinition('pie', '饼图', PieChartOutlined, pieSchema),
  chartDefinition('radar', '雷达图', RadarChartOutlined, radarSchema),
]

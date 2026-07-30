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

const colorSchemeField: PanelConfigField = {
  key: 'options.colorScheme',
  label: '配色主题',
  type: 'select',
  group: '样式',
  options: [
    { label: '默认', value: 'default' },
    { label: '活力', value: 'vivid' },
    { label: '沉稳', value: 'calm' },
  ],
}

const legendFields: PanelConfigField[] = [
  { key: 'options.showLegend', label: '显示图例', type: 'switch', group: '样式' },
  {
    key: 'options.legendPosition',
    label: '图例位置',
    type: 'select',
    group: '样式',
    options: [
      { label: '顶部', value: 'top' },
      { label: '底部', value: 'bottom' },
    ],
  },
]

const cartesianMapping: PanelConfigField[] = [
  { key: 'fieldMapping.x', label: '分类轴(X)', type: 'field', group: '数据映射' },
  { key: 'fieldMapping.y', label: '数值列(Y)', type: 'fields', group: '数据映射' },
]

const lineAreaSchema: PanelConfigField[] = [
  ...cartesianMapping,
  { key: 'options.smooth', label: '平滑曲线', type: 'switch', group: '样式' },
  { key: 'options.stack', label: '堆叠', type: 'switch', group: '样式' },
  { key: 'options.showLabel', label: '显示数值', type: 'switch', group: '样式' },
  { key: 'options.lineWidth', label: '线宽', type: 'number', group: '样式', placeholder: '默认 2' },
  ...legendFields,
  colorSchemeField,
]

const barSchema: PanelConfigField[] = [
  ...cartesianMapping,
  { key: 'options.horizontal', label: '横向柱状', type: 'switch', group: '样式' },
  { key: 'options.stack', label: '堆叠', type: 'switch', group: '样式' },
  { key: 'options.showLabel', label: '显示数值', type: 'switch', group: '样式' },
  ...legendFields,
  colorSchemeField,
]

const scatterSchema: PanelConfigField[] = [...cartesianMapping, ...legendFields, colorSchemeField]

const pieSchema: PanelConfigField[] = [
  { key: 'fieldMapping.name', label: '名称列', type: 'field', group: '数据映射' },
  { key: 'fieldMapping.value', label: '数值列', type: 'field', group: '数据映射' },
  { key: 'options.donut', label: '环形(甜甜圈)', type: 'switch', group: '样式' },
  { key: 'options.rose', label: '玫瑰图', type: 'switch', group: '样式' },
  { key: 'options.showLabel', label: '显示标签', type: 'switch', group: '样式' },
  ...legendFields,
  colorSchemeField,
]

const radarSchema: PanelConfigField[] = [
  { key: 'fieldMapping.name', label: '维度列', type: 'field', group: '数据映射' },
  { key: 'fieldMapping.y', label: '数值列(系列)', type: 'fields', group: '数据映射' },
  { key: 'options.area', label: '面积填充', type: 'switch', group: '样式' },
  ...legendFields,
  colorSchemeField,
]

function chartDefinition(
  type: string,
  name: string,
  icon: Component,
  schema: PanelConfigField[],
  supportsPanelFilters = false,
): PanelDefinition {
  return {
    type,
    name,
    category: '图表',
    icon: markRaw(icon),
    component: markRaw(ChartPanel),
    dataRequirement: { needsDataset: true, supportsPanelFilters },
    defaultDsl: () => ({
      layout: { x: 0, y: 0, w: 8, h: 6 },
      options: {},
      fieldMapping: {},
    }),
    configSchema: schema,
  }
}

export const chartPanelDefinitions: PanelDefinition[] = [
  chartDefinition('line', '折线图', LineChartOutlined, lineAreaSchema, true),
  chartDefinition('bar', '柱状图', BarChartOutlined, barSchema, true),
  chartDefinition('area', '面积图', AreaChartOutlined, lineAreaSchema, true),
  chartDefinition('scatter', '散点图', DotChartOutlined, scatterSchema, true),
  chartDefinition('pie', '饼图', PieChartOutlined, pieSchema),
  chartDefinition('radar', '雷达图', RadarChartOutlined, radarSchema),
]

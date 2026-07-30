/**
 * KPI 趋势卡片描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { RiseOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import KpiPanel from './KpiPanel.vue'

export const kpiPanelDefinition: PanelDefinition = {
  type: 'kpi',
  name: 'KPI 趋势',
  category: '指标',
  icon: markRaw(RiseOutlined),
  component: markRaw(KpiPanel),
  dataRequirement: { needsDataset: true },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 4 },
    options: { label: '指标名称', unit: '' },
    fieldMapping: {},
  }),
  configSchema: [
    { key: 'options.label', label: '指标名称', type: 'text', group: '内容' },
    { key: 'options.unit', label: '单位', type: 'text', group: '内容' },
    { key: 'fieldMapping.value', label: '取值列', type: 'field', group: '数据映射' },
    { key: 'fieldMapping.trend', label: '趋势列', type: 'field', group: '数据映射' },
    { key: 'fieldMapping.delta', label: '环比列(可选)', type: 'field', group: '数据映射' },
  ],
}

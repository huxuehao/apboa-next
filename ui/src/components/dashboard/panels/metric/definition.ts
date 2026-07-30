/**
 * 数据卡片面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { NumberOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import MetricPanel from './MetricPanel.vue'

export const metricPanelDefinition: PanelDefinition = {
  type: 'metric',
  name: '数据卡片',
  category: '指标',
  icon: markRaw(NumberOutlined),
  component: markRaw(MetricPanel),
  dataRequirement: { needsDataset: false },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 3 },
    options: { value: '0', label: '指标说明' },
    fieldMapping: {},
  }),
  configSchema: [
    { key: 'options.value', label: '静态值', type: 'text', group: '内容', placeholder: '未绑定数据集时展示' },
    { key: 'options.label', label: '描述', type: 'text', group: '内容' },
    { key: 'fieldMapping.value', label: '取值列', type: 'field', group: '数据映射' },
    { key: 'fieldMapping.label', label: '描述列', type: 'field', group: '数据映射' },
  ],
}

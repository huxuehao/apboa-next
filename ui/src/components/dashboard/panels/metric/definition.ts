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
  styleGroups: ['card', 'header', 'text'],
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 3 },
    options: { value: '0', label: '指标说明' },
    fieldMapping: {},
  }),
  configSchema: [
    { key: 'options.value', label: '静态值', type: 'text', group: '内容', placeholder: '未绑定数据集时展示' },
    { key: 'options.label', label: '描述', type: 'text', group: '内容' },
    { key: 'options.prefix', label: '前缀', type: 'text', group: '内容', placeholder: '如 ¥' },
    { key: 'options.suffix', label: '后缀/单位', type: 'text', group: '内容', placeholder: '如 元 / %' },
    { key: 'fieldMapping.value', label: '取值列', type: 'field', group: '数据映射' },
    { key: 'fieldMapping.label', label: '描述列', type: 'field', group: '数据映射' },
    { key: 'options.icon', label: '图标', type: 'icon', group: '装饰' },
    { key: 'options.iconColor', label: '图标色', type: 'color', group: '装饰' },
    { key: 'options.iconBg', label: '图标底色', type: 'color', group: '装饰' },
  ],
}

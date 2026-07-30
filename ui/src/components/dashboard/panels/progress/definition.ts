/**
 * 进度环/完成率面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { PieChartOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import ProgressPanel from './ProgressPanel.vue'

export const progressPanelDefinition: PanelDefinition = {
  type: 'progress',
  name: '进度环',
  category: '指标',
  icon: markRaw(PieChartOutlined),
  component: markRaw(ProgressPanel),
  dataRequirement: { needsDataset: true },
  styleGroups: ['card', 'header', 'text'],
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 5, h: 5 },
    options: { label: '完成率', style: 'ring', max: 100, value: 0 },
    fieldMapping: {},
  }),
  configSchema: [
    { key: 'options.label', label: '标签', type: 'text', group: '内容' },
    {
      key: 'options.style',
      label: '样式',
      type: 'select',
      group: '内容',
      options: [
        { label: '环形', value: 'ring' },
        { label: '条形', value: 'bar' },
      ],
    },
    { key: 'options.value', label: '静态当前值', type: 'number', group: '内容', placeholder: '未绑定数据集时' },
    { key: 'options.max', label: '目标值(默认)', type: 'number', group: '内容' },
    { key: 'fieldMapping.value', label: '当前值列', type: 'field', group: '数据映射' },
    { key: 'fieldMapping.target', label: '目标值列(可选)', type: 'field', group: '数据映射' },
  ],
}

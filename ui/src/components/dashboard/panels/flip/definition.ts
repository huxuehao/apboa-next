/**
 * 数字翻牌面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { FieldNumberOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import FlipNumberPanel from './FlipNumberPanel.vue'

export const flipNumberPanelDefinition: PanelDefinition = {
  type: 'flipNumber',
  name: '数字翻牌',
  category: '指标',
  icon: markRaw(FieldNumberOutlined),
  component: markRaw(FlipNumberPanel),
  dataRequirement: { needsDataset: true },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 4 },
    options: { value: '0', precision: 0, thousand: true },
    fieldMapping: {},
  }),
  configSchema: [
    { key: 'options.label', label: '标签', type: 'text', group: '内容' },
    { key: 'options.value', label: '静态值', type: 'text', group: '内容', placeholder: '未绑定数据集时展示' },
    { key: 'options.prefix', label: '前缀', type: 'text', group: '格式' },
    { key: 'options.suffix', label: '后缀', type: 'text', group: '格式' },
    { key: 'options.precision', label: '小数位', type: 'number', group: '格式' },
    { key: 'options.thousand', label: '千分位', type: 'switch', group: '格式' },
    { key: 'fieldMapping.value', label: '取值列', type: 'field', group: '数据映射' },
  ],
}

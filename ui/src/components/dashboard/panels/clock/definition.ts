/**
 * 时钟面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { ClockCircleOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import ClockPanel from './ClockPanel.vue'

export const clockPanelDefinition: PanelDefinition = {
  type: 'clock',
  name: '时钟',
  category: '内容',
  icon: markRaw(ClockCircleOutlined),
  component: markRaw(ClockPanel),
  dataRequirement: { needsDataset: false, supportsDataset: false },
  styleGroups: ['card', 'header', 'text'],
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 4 },
    options: { style: 'digital', showSeconds: true, showDate: true },
  }),
  configSchema: [
    {
      key: 'options.style',
      label: '样式',
      type: 'select',
      group: '样式',
      options: [
        { label: '数字时钟', value: 'digital' },
        { label: '翻牌时钟', value: 'flip' },
        { label: '模拟时钟', value: 'analog' },
        { label: '日期时间', value: 'datetime' },
      ],
    },
    { key: 'options.showSeconds', label: '显示秒', type: 'switch', group: '样式' },
    { key: 'options.showDate', label: '显示日期', type: 'switch', group: '样式' },
  ],
}

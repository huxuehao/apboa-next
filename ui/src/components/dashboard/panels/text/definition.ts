/**
 * 文本面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { FontSizeOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import TextPanel from './TextPanel.vue'

export const textPanelDefinition: PanelDefinition = {
  type: 'text',
  name: '文本',
  category: '内容',
  icon: markRaw(FontSizeOutlined),
  component: markRaw(TextPanel),
  dataRequirement: { needsDataset: false, supportsDataset: false },
  styleGroups: ['card', 'header', 'text'],
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 3 },
    options: { content: '', align: 'left' },
  }),
  configSchema: [
    { key: 'options.content', label: '文本内容', type: 'textarea', group: '内容' },
    {
      key: 'options.align',
      label: '对齐',
      type: 'select',
      group: '内容',
      options: [
        { label: '左对齐', value: 'left' },
        { label: '居中', value: 'center' },
        { label: '右对齐', value: 'right' },
      ],
    },
  ],
}

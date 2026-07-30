/**
 * Markdown 卡片面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { FileMarkdownOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import MarkdownPanel from './MarkdownPanel.vue'

export const markdownPanelDefinition: PanelDefinition = {
  type: 'markdown',
  name: 'Markdown',
  category: '内容',
  icon: markRaw(FileMarkdownOutlined),
  component: markRaw(MarkdownPanel),
  dataRequirement: { needsDataset: false, supportsDataset: false },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 8, h: 5 },
    options: { content: '## 标题\n\n- 列表项\n- **加粗**、[链接](https://example.com)' },
  }),
  configSchema: [
    { key: 'options.content', label: 'Markdown 内容', type: 'textarea', group: '内容' },
  ],
}

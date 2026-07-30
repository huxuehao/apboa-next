/**
 * Iframe 嵌入面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { BlockOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import IframePanel from './IframePanel.vue'

export const iframePanelDefinition: PanelDefinition = {
  type: 'iframe',
  name: 'Iframe 嵌入',
  category: '内容',
  icon: markRaw(BlockOutlined),
  component: markRaw(IframePanel),
  dataRequirement: { needsDataset: false, supportsDataset: false },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 8, h: 6 },
    options: {},
  }),
  configSchema: [
    { key: 'options.url', label: '嵌入地址', type: 'text', group: '内容', placeholder: 'https://...' },
  ],
}

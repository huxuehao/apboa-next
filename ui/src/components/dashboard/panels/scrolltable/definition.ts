/**
 * 滚动轮播表面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { UnorderedListOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import ScrollTablePanel from './ScrollTablePanel.vue'

export const scrollTablePanelDefinition: PanelDefinition = {
  type: 'scrollTable',
  name: '滚动轮播表',
  category: '表格',
  icon: markRaw(UnorderedListOutlined),
  component: markRaw(ScrollTablePanel),
  dataRequirement: { needsDataset: true },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 10, h: 7 },
    options: { rowsPerView: 5, interval: 2000, showHeader: true },
    fieldMapping: {},
  }),
  configSchema: [
    { key: 'options.columns', label: '显示列', type: 'columnPicker', group: '显示' },
    { key: 'options.showHeader', label: '显示表头', type: 'switch', group: '风格' },
    { key: 'options.rowsPerView', label: '可视行数', type: 'number', group: '滚动' },
    { key: 'options.interval', label: '滚动间隔(ms)', type: 'number', group: '滚动' },
  ],
}

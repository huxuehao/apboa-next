/**
 * 数据表格面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { TableOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import TablePanel from './TablePanel.vue'

export const tablePanelDefinition: PanelDefinition = {
  type: 'table',
  name: '数据表格',
  category: '表格',
  icon: markRaw(TableOutlined),
  component: markRaw(TablePanel),
  dataRequirement: { needsDataset: true, supportsPanelFilters: true },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 12, h: 6 },
    options: { pageSize: 10, showHeader: true, size: 'small' },
    fieldMapping: {},
  }),
  configSchema: [
    { key: 'options.columns', label: '显示列', type: 'columnPicker', group: '显示' },
    { key: 'options.pageSize', label: '每页行数', type: 'number', group: '显示' },
    { key: 'options.showHeader', label: '显示表头', type: 'switch', group: '风格' },
    { key: 'options.bordered', label: '显示边框', type: 'switch', group: '风格' },
    {
      key: 'options.size',
      label: '尺寸',
      type: 'select',
      group: '风格',
      options: [
        { label: '大', value: 'large' },
        { label: '中', value: 'middle' },
        { label: '小', value: 'small' },
      ],
    },
    { key: 'options.zebra', label: '斑马线', type: 'switch', group: '风格' },
  ],
}

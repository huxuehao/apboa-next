/**
 * 快捷方式面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { LinkOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import ShortcutPanel from './ShortcutPanel.vue'

export const shortcutPanelDefinition: PanelDefinition = {
  type: 'shortcut',
  name: '快捷方式',
  category: '内容',
  icon: markRaw(LinkOutlined),
  component: markRaw(ShortcutPanel),
  dataRequirement: { needsDataset: false, supportsDataset: false },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 3 },
    options: { name: '快捷方式', target: 'route' },
  }),
  configSchema: [
    { key: 'options.name', label: '名称', type: 'text', group: '内容', placeholder: '必填' },
    { key: 'options.desc', label: '描述', type: 'text', group: '内容' },
    { key: 'options.icon', label: '头像图标', type: 'icon', group: '内容' },
    { key: 'options.url', label: '跳转地址', type: 'text', group: '跳转', placeholder: '/agent 或 https://...' },
    {
      key: 'options.target',
      label: '跳转方式',
      type: 'select',
      group: '跳转',
      options: [
        { label: '当前页面(站内路由)', value: 'route' },
        { label: '新标签页打开', value: 'blank' },
        { label: '外部链接(当前页)', value: 'external' },
      ],
    },
  ],
}

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
    options: {
      name: '快捷方式',
      target: 'self',
      variant: 'left',
      avatarColor: '#1677ff',
      avatarBg: '#f0f5ff',
      avatarRadius: 8,
      avatarSize: 'medium',
      avatarBorder: false,
    },
  }),
  configSchema: [
    { key: 'options.name', label: '名称', type: 'text', group: '内容', placeholder: '必填' },
    { key: 'options.desc', label: '描述', type: 'text', group: '内容' },
    { key: 'options.url', label: '跳转地址', type: 'text', group: '跳转', placeholder: '/agent 或 https://...' },
    {
      key: 'options.target',
      label: '打开方式',
      type: 'select',
      group: '跳转',
      options: [
        { label: '本页面打开', value: 'self' },
        { label: '新页面打开', value: 'blank' },
      ],
    },
    {
      key: 'options.variant',
      label: '风格',
      type: 'select',
      group: '外观',
      options: [
        { label: '左图标 右文字', value: 'left' },
        { label: '上图标 下文字(居中)', value: 'center' },
        { label: '极简(图标+名称)', value: 'minimal' },
      ],
    },
    { key: 'options.icon', label: '头像图标', type: 'icon', group: '内容' },
    {
      key: 'options.avatarSize',
      label: '头像大小',
      type: 'select',
      group: '外观',
      options: [
        { label: '小', value: 'small' },
        { label: '中', value: 'medium' },
        { label: '大', value: 'large' },
      ],
    },
    { key: 'options.avatarColor', label: '头像颜色', type: 'color', group: '外观' },
    { key: 'options.avatarBg', label: '头像背景', type: 'color', group: '外观' },
    { key: 'options.avatarRadius', label: '头像圆角', type: 'number', group: '外观', placeholder: '像素，大值即圆形' },
    { key: 'options.avatarBorder', label: '头像边框', type: 'switch', group: '外观' },
  ],
}

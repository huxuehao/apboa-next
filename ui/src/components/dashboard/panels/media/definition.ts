/**
 * 图片/视频面板描述符
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { PictureOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import MediaPanel from './MediaPanel.vue'

export const mediaPanelDefinition: PanelDefinition = {
  type: 'media',
  name: '图片/视频',
  category: '内容',
  icon: markRaw(PictureOutlined),
  component: markRaw(MediaPanel),
  dataRequirement: { needsDataset: false, supportsDataset: false },
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 6, h: 5 },
    options: { mediaType: 'image', fit: 'contain', muted: true },
  }),
  configSchema: [
    {
      key: 'options.mediaType',
      label: '类型',
      type: 'select',
      group: '内容',
      options: [
        { label: '图片', value: 'image' },
        { label: '视频', value: 'video' },
      ],
    },
    { key: 'options.url', label: '地址', type: 'text', group: '内容', placeholder: 'https://...' },
    {
      key: 'options.fit',
      label: '填充方式',
      type: 'select',
      group: '显示',
      options: [
        { label: '适应(contain)', value: 'contain' },
        { label: '裁剪填充(cover)', value: 'cover' },
        { label: '拉伸(fill)', value: 'fill' },
      ],
    },
    { key: 'options.autoplay', label: '自动播放(视频)', type: 'switch', group: '视频' },
    { key: 'options.loop', label: '循环(视频)', type: 'switch', group: '视频' },
    { key: 'options.muted', label: '静音(视频)', type: 'switch', group: '视频' },
  ],
}

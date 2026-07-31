/**
 * 自定义组件面板描述符：渲染 portal 目录下的业务组件。
 *
 * @author huxuehao
 */
import { markRaw } from 'vue'
import { AppstoreAddOutlined } from '@ant-design/icons-vue'
import type { PanelDefinition } from '@/types/dashboard'
import CustomPanel from './CustomPanel.vue'

export const customPanelDefinition: PanelDefinition = {
  type: 'custom',
  name: '自定义组件',
  category: '内容',
  icon: markRaw(AppstoreAddOutlined),
  component: markRaw(CustomPanel),
  dataRequirement: { needsDataset: false, supportsDataset: false },
  styleGroups: ['card', 'header'],
  defaultDsl: () => ({
    layout: { x: 0, y: 0, w: 8, h: 5 },
    options: { component: '', propsList: [] },
  }),
  configSchema: [
    { key: 'options.component', label: '组件', type: 'portalComponent', group: '内容' },
    { key: 'options.propsList', label: '组件 props', type: 'propsList', group: '内容' },
  ],
}

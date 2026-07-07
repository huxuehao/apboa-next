import { markRaw } from 'vue'
import CtrlAddNodeGuide from './CtrlAddNodeGuide.vue'
import QuickInputGuide from './QuickInputGuide.vue'
import FormatterGuide from './FormatterGuide.vue'
import type { GuideEntry } from '../types'

/**
 * 指南注册中心
 *
 * 新增指南步骤：
 * 1. 在 content/ 下创建 YourGuide.vue（内容组件，只负责渲染右侧内容区）
 * 2. 在此数组中追加条目，指定 id、title 和 component
 *
 * 注意：component 使用 markRaw 标记，避免 Vue 对纯展示组件做深度响应式代理
 */
export const guideEntries: GuideEntry[] = [
  {
    id: 'ctrl-add-node',
    title: 'Ctrl 添加节点',
    component: markRaw(CtrlAddNodeGuide),
  },
  {
    id: 'quick-input',
    title: '快捷输入绑定名',
    component: markRaw(QuickInputGuide),
  },
  {
    id: 'formatter',
    title: '参数模板格式',
    component: markRaw(FormatterGuide),
  },
  // 未来在此追加：
  // { id: 'node-connection', title: '节点连线与数据流', component: markRaw(NodeConnectionGuide) },
  // { id: 'template-syntax', title: '模板语法详解',     component: markRaw(TemplateSyntaxGuide) },
]

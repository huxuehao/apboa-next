/**
 * Ant Design 图标注册表：为快捷方式等面板提供可选图标（图标选择器数据源）。
 * 程序化注册整个 Outlined 图标族，自动覆盖全量并与项目描边风格保持一致
 * （不混入 Filled/TwoTone，避免选择器视觉不统一）。
 *
 * @author huxuehao
 */
import { markRaw, type Component } from 'vue'
import * as AntdIcons from '@ant-design/icons-vue'

export interface IconEntry {
  name: string
  component: Component
}

/** 从 icons-vue 命名空间中筛出全部 Outlined 图标组件（工具函数如 createFromIconfontCN 不以 Outlined 结尾，天然排除） */
const entries: IconEntry[] = Object.entries(AntdIcons)
  .filter(([name, comp]) => name.endsWith('Outlined') && !!comp)
  .map(([name, comp]) => ({ name, component: markRaw(comp as Component) }))
  .sort((a, b) => a.name.localeCompare(b.name))

const iconMap = new Map(entries.map((e) => [e.name, e.component]))

/** 全部可选图标 */
export function listIcons(): IconEntry[] {
  return entries
}

/** 按名称解析图标组件，未命中返回 undefined */
export function resolveIcon(name?: string): Component | undefined {
  return name ? iconMap.get(name) : undefined
}

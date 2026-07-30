/**
 * 数据集使用说明注册表：按面板归类，未来新增面板只需注册一条说明。
 *
 * @author huxuehao
 */
import type { Component } from 'vue'

/** 一条数据集使用说明 */
export interface DatasetGuideEntry {
  /** 唯一键 */
  key: string
  /** 导航标题 */
  title: string
  /** 覆盖的面板类型（用于未来按面板联动定位说明） */
  panelTypes: string[]
  /** 说明内容组件 */
  component: Component
}

const registry: DatasetGuideEntry[] = []

/** 注册一条使用说明 */
export function registerGuide(entry: DatasetGuideEntry) {
  if (registry.some((g) => g.key === entry.key)) return
  registry.push(entry)
}

/** 列出全部使用说明 */
export function listGuides(): DatasetGuideEntry[] {
  return registry
}

/** 按面板类型查找对应说明（未来定位用） */
export function findGuideByPanelType(type: string): DatasetGuideEntry | undefined {
  return registry.find((g) => g.panelTypes.includes(type))
}

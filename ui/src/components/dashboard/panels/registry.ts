/**
 * 面板注册表：面板可插拔的核心。新增面板 = 注册一个描述符。
 *
 * @author huxuehao
 */
import type { PanelDefinition } from '@/types/dashboard'

const registry = new Map<string, PanelDefinition>()

/** 注册面板描述符 */
export function registerPanel(def: PanelDefinition) {
  registry.set(def.type, def)
}

/** 按类型获取面板描述符 */
export function getPanel(type: string): PanelDefinition | undefined {
  return registry.get(type)
}

/** 列出所有已注册面板 */
export function listPanels(): PanelDefinition[] {
  return Array.from(registry.values())
}

/** 按 category 分组列出面板 */
export function listPanelsByCategory(): Record<string, PanelDefinition[]> {
  return listPanels().reduce<Record<string, PanelDefinition[]>>((acc, def) => {
    ;(acc[def.category] ||= []).push(def)
    return acc
  }, {})
}

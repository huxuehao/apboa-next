/**
 * 面板操作按钮运行时注册表（通用机制）。
 * 面板组件挂载后注册标题栏动作与可选 refresh 处理器，
 * PanelRenderer 读取渲染按钮，PanelConfigDrawer 读取生成显隐开关。
 *
 * @author huxuehao
 */
import { reactive } from 'vue'
import type { PanelAction } from '@/types/dashboard'

interface PanelActionEntry {
  actions: PanelAction[]
  refresh?: () => void
}

const registry = reactive(new Map<string, PanelActionEntry>())

/** 注册面板的操作按钮与可选 refresh 处理器（覆盖式，组件切换时重复调用即可） */
export function registerPanelActions(panelId: string, actions: PanelAction[], refresh?: () => void) {
  registry.set(panelId, { actions, refresh })
}

/** 注销（面板卸载/组件切换时） */
export function unregisterPanelActions(panelId: string) {
  registry.delete(panelId)
}

/** 获取面板已注册的操作按钮 */
export function getPanelActions(panelId: string): PanelAction[] {
  return registry.get(panelId)?.actions || []
}

/** 获取面板的 refresh 处理器（定时刷新对无数据集面板生效） */
export function getPanelRefresh(panelId: string): (() => void) | undefined {
  return registry.get(panelId)?.refresh
}

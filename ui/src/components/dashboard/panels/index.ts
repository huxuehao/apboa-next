/**
 * 面板统一注册入口。新增面板：实现组件 + 描述符，在此注册即可。
 *
 * @author huxuehao
 */
import { registerPanel } from './registry'
import { metricPanelDefinition } from './metric/definition'
import { kpiPanelDefinition } from './kpi/definition'
import { progressPanelDefinition } from './progress/definition'
import { tablePanelDefinition } from './table/definition'
import { chartPanelDefinitions } from './chart/definition'
import { shortcutPanelDefinition } from './shortcut/definition'
import { scrollTablePanelDefinition } from './scrolltable/definition'
import { flipNumberPanelDefinition } from './flip/definition'
import { textPanelDefinition } from './text/definition'
import { mediaPanelDefinition } from './media/definition'
import { iframePanelDefinition } from './iframe/definition'
import { clockPanelDefinition } from './clock/definition'
import { markdownPanelDefinition } from './markdown/definition'
import { customPanelDefinition } from './custom/definition'

let registered = false

/** 注册所有内置面板（幂等） */
export function registerBuiltinPanels() {
  if (registered) return
  registerPanel(metricPanelDefinition)
  registerPanel(kpiPanelDefinition)
  registerPanel(progressPanelDefinition)
  registerPanel(flipNumberPanelDefinition)
  registerPanel(tablePanelDefinition)
  registerPanel(scrollTablePanelDefinition)
  chartPanelDefinitions.forEach(registerPanel)
  registerPanel(shortcutPanelDefinition)
  registerPanel(textPanelDefinition)
  registerPanel(mediaPanelDefinition)
  registerPanel(iframePanelDefinition)
  registerPanel(clockPanelDefinition)
  registerPanel(markdownPanelDefinition)
  registerPanel(customPanelDefinition)
  registered = true
}

export * from './registry'

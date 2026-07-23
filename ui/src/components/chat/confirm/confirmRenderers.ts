/**
 * HITL 定制确认渲染器注册表：工具名（toolId / MCP 原生名，与 ToolUseBlock.name 一致）
 * → 定制确认组件。命中则由该组件以业务语义渲染确认 UI（解引用编号、展示业务对象、
 * 提供业务级修改交互）；未命中回退 schema 通用表单，再回退 JSON（见 ToolConfirmPanel）。
 *
 * 新增业务定制卡三步：
 * 1. 写组件（props { name, input, fields }，emit decide({ approved, input? })——
 *    input 仅在用户修改过参数时携带，原样确认则省略走原参数路径）；
 * 2. 组件内的数据解引用（编号→业务对象）替换为真实业务接口调用，
 *    鉴权由业务接口自行分辨（含 chatKey 匿名嵌入场景）；
 * 3. 在下方注册表加一行映射。
 */
import type { Component } from 'vue'

const registry: Record<string, Component> = {}

/** 按工具名解析定制确认渲染器；未注册返回 undefined（走通用表单/JSON 回退） */
export function resolveConfirmRenderer(toolName: string): Component | undefined {
  return registry[toolName]
}

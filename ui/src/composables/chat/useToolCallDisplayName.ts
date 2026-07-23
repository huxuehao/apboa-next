import { ref } from 'vue'
import * as agentApi from '@/api/agent'
import * as toolApi from '@/api/tool'
import * as mcpApi from '@/api/mcp'
import type { AgentDefinitionDTO, McpServerDTO, ToolDTO } from '@/types'

/**
 * 工具调用卡片显示名翻译层（模块级单例）
 *
 * 执行过程卡片的 name 是运行时内部标识：
 * - 子智能体：agentCode 小写（后端 ToolkitFactory 注册 SubAgentConfig.toolName 的规则）
 * - 普通工具：toolId（后端 toolkit.getTool(toolId) 的注册 key）
 * - MCP 工具：MCP 原始 toolName（LazyMcpAgentTool.getName() 即 schema 名，无前缀）
 *
 * 这里维护「全量智能体 + 全量工具 + 全量 MCP 工具」三张映射表做显示翻译
 * （数据量小，全量拉取可覆盖历史消息里已被移除的资源）：
 * 子智能体 → 智能体名称；工具 → 工具名称；MCP 工具 → 「MCP服务名 · 工具名」。
 * 仅翻译显示层，HITL 回传等数据流仍用原始 name。
 * 首个引用组件挂载时幂等加载；失败重置标志，下次组件创建时重试。
 *
 * @author vaulka
 */

/** agentCode(小写) -> 智能体名称 */
const agentNameMap = ref<Record<string, string>>({})
/** toolId -> 工具名称 */
const toolNameMap = ref<Record<string, string>>({})
/** MCP toolName -> 「MCP服务名 · 工具名」 */
const mcpToolNameMap = ref<Record<string, string>>({})
/**
 * agentscope 框架内置运行时工具 → 中文显示名（静态兜底）。
 * 这类工具由框架/工厂直接注册进 toolkit，不入 tool_config 库，上面三张 DB 映射覆盖不到。
 * 后续发现同类裸名工具往这里添即可。
 */
const FRAMEWORK_TOOL_NAMES: Record<string, string> = {
  load_skill_through_path: '读取技能',
  view_text_file: '查看文件',
  write_text_file: '写入文件',
  insert_text_file: '插入文本',
  search_replace_file: '查找替换',
  list_directory: '列出目录',
  load_file_text_content: '读取附件',
  execute_shell_command: '执行 Shell 命令',
  context_reload: '重载上下文',
}
/** 幂等加载标志 */
let loaded = false

async function ensureLoaded(): Promise<void> {
  if (loaded) return
  loaded = true
  try {
    const [agentRes, toolRes, mcpServerRes] = await Promise.all([
      agentApi.page({ current: 1, size: 9999 } as unknown as AgentDefinitionDTO),
      toolApi.page({ current: 1, size: 9999 } as unknown as ToolDTO),
      mcpApi.page({ current: 1, size: 9999 } as unknown as McpServerDTO)
    ])

    const am: Record<string, string> = {}
    for (const a of agentRes.data?.data?.records || []) {
      const code = a.agentCode?.toLowerCase()
      if (code && a.name && !(code in am)) {
        am[code] = a.name
      }
    }
    agentNameMap.value = am

    const tm: Record<string, string> = {}
    for (const t of toolRes.data?.data?.records || []) {
      if (t.toolId && t.name && !(t.toolId in tm)) {
        tm[t.toolId] = t.name
      }
    }
    toolNameMap.value = tm

    // MCP：按服务拉工具目录（查的是 DB 缓存目录，不要求服务在线），单个失败不影响整体
    const servers = mcpServerRes.data?.data?.records || []
    const toolLists = await Promise.all(
      servers.map(s =>
        mcpApi.listTools(String(s.id))
          .then(r => ({ serverName: s.name, tools: r.data?.data || [] }))
          .catch(() => ({ serverName: s.name, tools: [] }))
      )
    )
    const mm: Record<string, string> = {}
    for (const { serverName, tools } of toolLists) {
      for (const t of tools) {
        if (t.toolName && !(t.toolName in mm)) {
          mm[t.toolName] = `${serverName} · ${t.toolName}`
        }
      }
    }
    mcpToolNameMap.value = mm
  } catch {
    // 加载失败允许下次重试，翻译层缺数据时自然回退原值
    loaded = false
  }
}

export function useToolCallDisplayName() {
  // fire-and-forget：组件创建即触发加载，映射就绪后响应式自动刷新显示
  ensureLoaded().then(() => {})

  /**
   * 翻译工具调用名：子智能体(agentCode) → 智能体名称；工具(toolId) → 工具名称；
   * MCP 工具(toolName) → 「MCP服务名 · 工具名」；未命中（已删除资源、映射未加载完）回退原值
   */
  const resolveToolCallName = (name?: string | null): string => {
    if (!name) return ''
    return agentNameMap.value[name.toLowerCase()]
      || toolNameMap.value[name]
      || mcpToolNameMap.value[name]
      || FRAMEWORK_TOOL_NAMES[name]
      || name
  }

  return { resolveToolCallName }
}

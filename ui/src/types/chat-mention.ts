/**
 * 聊天 @mention 资源类型定义
 * 支持工作空间文件、Agent 工具、Agent 技能等多种可被引用的资源
 *
 * @author huxuehao
 */

/**
 * 资源类型枚举
 * 新增 kind 时只需：
 * 1. 在此处增加字面量
 * 2. 在 useResourceCategories 注册表中补充元数据
 * 3. 如需在消息侧渲染，在 TaggedContentRenderer 中注册组件
 */
export type ResourceKind =
  | 'workspace-file'
  | 'agent-tool'
  | 'agent-skill'
  | 'agent-mcp'
  | 'agent-sub-agent'
  | 'agent-workflow'

/**
 * Agent 工具项（用于 mock 与后续接入）
 */
export interface AgentToolItem {
  /** 工具唯一标识 */
  id: string
  /** 工具名称 */
  name: string
  /** 工具描述 */
  description?: string
}

/**
 * Agent 技能项
 */
export interface AgentSkillItem {
  /** 技能唯一标识 */
  id: string
  /** 技能名称（= 标签值，发送给 agent 用） */
  name: string
  /** 展示别名（仅展示层，值仍用 name） */
  alias?: string
  /** 技能描述 */
  description?: string
}

/**
 * Agent MCP 工具项（按 server 分组的工具，拍平后带 server 标注）
 */
export interface AgentMcpToolItem {
  /** 工具名（= LLM 调用名 = <agent-mcp> 标签 content = 同 kind 内唯一标识） */
  name: string
  /** 工具描述 */
  description?: string
  /** 所属 MCP 服务 ID（下拉分组用） */
  serverId: string
  /** 所属 MCP 服务名（下拉分组标题用） */
  serverName: string
}

/**
 * Agent 子智能体项（子智能体以 Agent-as-Tool 注册，agentCode 小写即 LLM 调用名）
 */
export interface AgentSubAgentItem {
  /** agentCode 小写（= LLM 调用名 = <agent-sub-agent> 标签 content） */
  code: string
  /** 子智能体名称（展示用） */
  name: string
  /** 描述 */
  description?: string
}

/**
 * Agent 工作流项（工作流以 WorkflowTool 注册，工作流名即 LLM 调用名）
 */
export interface AgentWorkflowItem {
  /** 工作流名（= LLM 调用名 = <agent-workflow> 标签 content = 展示名） */
  name: string
  /** 描述（工作流 remark） */
  description?: string
}

/**
 * 流转于 Dropdown -> Editor 的统一资源项
 * 任意 ResourceKind 的资源都被归一化为此结构
 */
export interface MentionResourceItem {
  /** 资源类型 */
  kind: ResourceKind
  /** 同 kind 内唯一标识 */
  id: string
  /** 主标题 */
  name: string
  /** 展示别名（仅展示层，选中/插入仍用 id/name） */
  alias?: string
  /** 副标题（描述、路径等） */
  description?: string
  /** 原始数据，便于业务侧二次处理 */
  raw?: unknown
}

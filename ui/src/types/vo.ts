/**
 * VO类型定义
 *
 * @author huxuehao
 */

import type {
  ToolChoiceStrategy,
  HookType,
  ToolType,
  AuthType,
  ModelCategory,
  ModelType,
  HealthStatus,
  KbType,
  McpActivationStatus,
  McpFailureSource,
  McpToolExposureMode,
  McpMode,
  McpProtocol,
  SensitiveWordAction,
  RAGMode,
  TenantRole,
  TenantJoinRequestStatus
} from './enums'
import type { AgentA2A, HookConfig, JobInfo, ToolConfig, SkillPackage } from '@/types/entity.ts'

/**
 * 租户VO
 */
export interface TenantVO {
  id: string | number
  name: string
  code: string
  description: string
  status: number
  contactName: string
  contactEmail: string
  config: string
  discoverable: boolean
  joinable: boolean
  joinApprovalRequired: boolean
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
}

/**
 * 租户成员VO（含用户信息与租户角色）
 */
export interface TenantMemberVO {
  accountId: string | number
  nickname: string
  email: string
  username: string
  tenantRole: TenantRole
  /** 加入时间 */
  joinedAt: string
  /** 是否已启用 */
  enabled: boolean
}

/**
 * 租户加入申请VO（含申请人信息）
 */
export interface TenantJoinRequestVO {
  id: string | number
  tenantId: string | number
  accountId: string | number
  applicantName: string
  applicantUsername: string
  status: TenantJoinRequestStatus
  message: string
  reviewedBy: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 账号VO
 */
export interface AccountVO {
  id: string | number
  nickname: string
  email: string
  username: string
  enabled: boolean
  tenantRole?: string
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
  rememberLastTenant?: boolean
}

/**
 * 智能体常用问题
 */
export interface CommonQuestion {
  /** 图标名称（对应 @ant-design/icons-vue 组件名，不在映射表中时不渲染图标） */
  icon?: string
  /** 图标颜色 */
  color?: string
  /** 卡片标题 */
  title: string
  /** 点击卡片后发送的问题内容 */
  question: string
}

/**
 * 智能体候选模型选项（对话页模型切换下拉数据源，detail 拼装）
 */
export interface AgentModelOptionVO {
  id: string
  /** 模型显示名 */
  name: string
  /** 供应商类型（分组/图标用） */
  providerType?: string | null
  /** 模型描述（下拉菜单第二行） */
  description?: string | null
  /** 展示图标（antd 图标组件名；空=默认 DeploymentUnitOutlined） */
  logo?: string | null
  /** 展示图标颜色（hex；空=默认 #0F74FF） */
  logoColor?: string | null
  /** 是否 agent 默认模型 */
  isDefault?: boolean
  /** 该模型是否支持会话级思考开关（切模型后据此显隐思考按钮） */
  thinkingSwitchSupported?: boolean
}

/**
 * 智能体定义VO
 */
export interface AgentDefinitionVO {
  id: string | number
  agentType: 'CUSTOM' | 'A2A'
  name: string
  agentCode: string
  description: string
  commonQuestions?: CommonQuestion[] | null
  /** 常用问题是否在对话中常驻显示（DB 默认 1） */
  commonQuestionsPinned?: boolean
  modelConfigId: string
  /** 额外候选对话模型 id（不含默认 modelConfigId；detail 回显 + 保存提交） */
  models?: string[]
  /** 各候选模型的参数覆盖（key=候选 id；空/缺省=跟随模型默认。默认模型的覆盖走 modelParamsOverride） */
  modelsParamsOverride?: Record<string, Record<string, unknown> | null>
  /** 候选模型选项（默认+额外候选，detail 拼装；对话页模型切换下拉数据源） */
  modelOptions?: AgentModelOptionVO[]
  /** 语音识别模型配置ID（null=不启用语音输入） */
  asrModelConfigId?: string | null
  /** 语音合成模型配置ID（null=不启用语音播报） */
  ttsModelConfigId?: string | null
  modelParamsOverride: Record<string, unknown> | null
  /** 语音合成(TTS)参数覆盖（agent 级，如 {voice:'Cherry'}）；null/不传=跟随模型默认音色 */
  ttsParamsOverride: Record<string, unknown> | null
  skill: string[]
  workflow: string[]
  tool: string[]
  mcp: string[]
  mcpBindings: AgentMcpBindingVO[]
  hook: string[]
  subAgent: string[]
  knowledgeBase: string[]
  toolChoiceStrategy: ToolChoiceStrategy
  specificToolName: string
  systemPromptTemplateId: string
  followTemplate: boolean
  systemPrompt: string
  sensitiveWordConfigId: string
  sensitiveFilterEnabled: boolean
  maxIterations: number
  /** 月度成本预算（元）；null=不限额，当月已计价成本达到即拒绝新对话 */
  monthlyBudget?: number | null
  enablePlanning: boolean
  maxSubtasks: number
  requirePlanConfirmation: boolean
  enableMemory: boolean
  enableMemoryCompression: boolean
  showToolProcess: boolean
  /** 当前模型是否支持会话级思考模式开关（派生字段，由 model.thinking==true 判定；驱动思考按钮显隐） */
  thinkingSwitchSupported?: boolean
  memoryCompressionConfig: Record<string, unknown> | null
  structuredOutputEnabled: boolean
  structuredOutputSchema: Record<string, unknown> | null
  structuredOutputReminder: 'PROMPT' | 'TOOL_CHOICE'
  version: string
  tag: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
  agentA2A: AgentA2A
  jobInfo: JobInfo
  studioConfigId: string | null
  codeExecutionConfigId: string | null
  longTermMemoryConfigId: string | null
}

/**
 * 对话页面智能体聚合上下文VO：detail+avatar+allowFileType+enabledTools+enabledSkills 合一
 */
export interface AgentChatContextVO {
  detail: AgentDefinitionVO
  avatar: string | null
  allowFileType: string[]
  enabledTools: ToolConfig[]
  enabledSkills: SkillPackage[]
  enabledMcp: EnabledMcpServerVO[]
  /** 启用的子智能体（轻量列；agentCode 小写即 LLM 工具调用名，供 @ 引用） */
  enabledSubAgents?: { id: string; agentCode: string; name: string; description?: string }[]
  /** 启用且已发布的工作流（轻量列；name 即 LLM 工具调用名，供 @ 引用） */
  enabledWorkflows?: { id: string; name: string; remark?: string }[]
}

/**
 * chat-context 里 agent 启用的 MCP 服务及其工具（按 server 分组，供 @ 提及具体 MCP 工具）。
 * 注意：与下方管理页的 McpServerVO 含义不同，勿混用。
 */
export interface EnabledMcpServerVO {
  serverId: string | number
  serverName: string
  tools: { name: string; description?: string }[]
}

/**
 * Hook配置VO
 */
export interface HookConfigVO extends HookConfig{
  used: string[]
}

/**
 * 知识库配置VO
 */
export interface KnowledgeBaseConfigVO {
  id: string | number
  name: string
  kbType: KbType
  ragMode: RAGMode
  description: string
  connectionConfig: Record<string, unknown> | null
  endpointConfig: Record<string, unknown> | null
  retrievalConfig: Record<string, unknown> | null
  rerankingConfig: Record<string, unknown> | null
  queryRewriteConfig: Record<string, unknown> | null
  metadataFilters: Record<string, unknown> | null
  httpConfig: Record<string, unknown> | null
  healthStatus: HealthStatus
  lastSyncTime: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
}

/**
 * MCP服务器VO
 */
export interface McpServerVO {
  id: string | number
  name: string
  protocol: McpProtocol
  mode: McpMode
  timeout: number
  protocolConfig: Record<string, unknown> | null
  description: string
  /** 身份断言 audience（空则该 MCP 不注入断言） */
  audience?: string | null
  healthStatus: HealthStatus
  lastHealthCheck: string
  activationStatus: McpActivationStatus
  activationMessage: string
  failureSource: McpFailureSource
  activationStatusChangedAt: string | null
  lastActivationTime: string | null
  lastToolSyncTime: string | null
  toolCount: number
  availableToolCount: number
  runtimeFailThreshold: number
  idleTimeoutMs: number
  needsSync: boolean
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
}

/**
 * Agent MCP 绑定信息
 */
export interface AgentMcpBindingVO {
  mcpServerId: string
  exposureMode: McpToolExposureMode
  mcpToolIds: string[]
}

/**
 * MCP 工具 VO
 */
export interface McpToolVO {
  id: string | number
  mcpServerId: string | number
  toolName: string
  description: string
  inputSchema: Record<string, unknown> | null
  outputSchema: Record<string, unknown> | null
  enabled: boolean
  needConfirm: boolean
  missing: boolean
  sort: number
  lastDiscoveredAt: string | null
  lastSeenAt: string | null
}

/**
 * MCP 工具调试调用结果
 */
export interface McpToolDebugResultVO {
  success: boolean
  toolName: string
  content: unknown
  errorMessage: string | null
  durationMs: number
  executedAt: string
}

/**
 * MCP 工具调试历史记录项
 */
export interface McpDebugHistoryItem {
  id: string
  toolId: string | number
  toolName: string
  serverName: string
  input: Record<string, unknown>
  result: McpToolDebugResultVO
  executedAt: string
}

/**
 * 模型配置VO
 */
export interface ModelConfigVO {
  id: string | number
  providerId: string
  name: string
  modelId: string
  /** 展示图标（antd 图标组件名；空=默认 DeploymentUnitOutlined） */
  logo?: string | null
  /** 展示图标颜色（hex；空=默认 #0F74FF） */
  logoColor?: string | null
  /** 模型用途（LLM=对话生成 / ASR=语音识别） */
  category?: ModelCategory
  modelType: ModelType[] | null
  description: string
  streaming: boolean
  thinking: boolean
  contextWindow: number
  maxTokens: number
  temperature: number
  topP: number
  topK: number
  repeatPenalty: number
  seed: string
  extendConfig: Record<string, any> | null
  connectivityStatus?: string
  connectivityMessage?: string
  lastConnectivityCheck?: string
  /** 输入单价（元/百万token；null=未配价，0=免费/本地） */
  inputPrice?: number | null
  /** 输出单价（元/百万token；null=未配价，0=免费/本地） */
  outputPrice?: number | null
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
}

/**
 * 模型提供商VO
 */
export interface ModelProviderVO {
  id: string | number
  type: string
  name: string
  description: string
  baseUrl: string
  authType: AuthType
  apiKey: string
  envVarName: string
  enabled: boolean
  configMeta: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
}

/**
 * 敏感词配置VO
 */
export interface SensitiveWordConfigVO {
  id: string | number
  category: string
  name: string
  description: string
  words: string[] | null
  action: SensitiveWordAction
  replacement: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
}

/**
 * 技能包VO
 */
export interface SkillPackageVO {
  id: string | number
  name: string
  alias?: string
  description: string
  category: string
  skillType?: 'BUILTIN' | 'CUSTOM'
  classPath?: string
  scopeType?: 'GLOBAL' | 'TENANT'
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
  tools:string[]
}

/**
 * 技能包文件树节点
 */
export interface SkillFileTreeNode {
  /** 文件名或目录名 */
  name: string
  /** 相对路径，如 "scripts/helper.py" */
  path: string
  /** 是否目录 */
  directory: boolean
  /** DB id（入库文件才有，纯文件系统文件为 null） */
  fileId: string | null
  /** 文件类型（仅入库文件） */
  fileType: 'SKILL_MD' | 'REFERENCES' | 'EXAMPLES' | 'SCRIPTS' | null
  /** 文件扩展名，目录为空 */
  extension: string
  /** 文件大小（字节），目录为 0 */
  fileSize: number
  /** 子节点 */
  children: SkillFileTreeNode[]
  /** 文件内容（前端加载后填充） */
  content?: string
  /** 前端状态：是否有未保存修改 */
  dirty?: boolean
}

/**
 * 技能包导入结果
 */
export interface SkillImportResult {
  importedCount: number
  skippedCount: number
  totalCount: number
  hintMessage?: string | null
}

/**
 * 系统提示词模板VO
 */
export interface SystemPromptTemplateVO {
  id: string | number
  category: string
  name: string
  description: string
  content: string
  usageCount: number
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
}

/**
 * 工具VO
 */
export interface ToolVO {
  id: string | number
  name: string
  toolId: string
  description: string
  category: string
  language: string
  toolType: ToolType
  inputSchema: any[] | null
  outputSchema: any[] | null
  classPath: string
  code: string
  needConfirm: boolean,
  version: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
}

/**
 * 工具调试调用结果（结构对齐 McpToolDebugResultVO，后端 ToolDebugResultVO）
 */
export interface ToolDebugResultVO {
  success: boolean
  toolName: string
  content: unknown
  errorMessage: string | null
  durationMs: number
  executedAt: string
}

/**
 * 工具调试历史记录项
 */
export interface ToolDebugHistoryItem {
  id: string
  toolId: string | number
  toolName: string
  category: string
  input: Record<string, unknown>
  result: ToolDebugResultVO
  executedAt: string
}

/**
 * 聊天会话VO
 */
export interface ChatSessionVO {
  id: string | number
  userId: string
  agentId: string
  currentMessageId: string | null
  title: string | null
  isPinned: boolean
  pinTime: string | null
  messageTable: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 会话状态VO：confirm-mode + thinking-mode 聚合
 */
export interface ChatSessionStateVO {
  confirmMode: 'AUTO_APPROVE' | 'MANUAL' | 'AUTO_REJECT'
  thinkingMode: boolean
  /** 会话模型覆盖（候选 modelConfigId；null=用 agent 默认模型） */
  modelConfigId?: string | null
}

/**
 * 聊天消息VO
 */
export interface ChatMessageVO {
  id: string | number
  sessionId: string
  role: string
  content: string
  parentId: string | null
  path: string
  depth: number
  /** 消息元数据 JSON（durationMs/iterationCount/inputTokens/outputTokens/totalTokens），仅 assistant 正文有 */
  meta?: string
  createdAt: string
}

/**
 * Studio配置VO
 */
export interface StudioConfigVO {
  id: string
  url: string
  project: string
}

/**
 * 访问秘钥VO
 */
export interface SecretKeyVO {
  id: string | number
  name: string
  /** value 在列表中已脱敏，创建时返回完整值 */
  value: string
  expireTime: string | null
  remark: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
}

/**
 * 代码执行配置VO
 */
export interface CodeExecutionConfigVO {
  id: string
  configName: string
  workDir?: string
  uploadDir?: string
  autoUpload?: boolean
  enableShell?: boolean
  enableRead?: boolean
  enableWrite?: boolean
  command?: string[] | null
}

/**
 * 长期记忆配置VO
 */
export interface LongTermMemoryConfigVO {
  id: string
  configName: string
  memoryType: 'MEM0' | 'REME' | 'BAILIAN'
  config: Record<string, unknown> | null
  enabled: boolean
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  used: string[]
}

/**
 * 趋势数据项
 */
export interface TrendItem {
  date: string
  value: number
}

/**
 * 智能体统计分析VO
 */
export interface AgentStatisticsVO {
  sessionTrend: TrendItem[]
  activeUserTrend: TrendItem[]
  messageTrend: TrendItem[]
  avgRoundsTrend: TrendItem[]
}

/**
 * 工作空间文件树节点 VO
 */
export interface WorkspaceFileNode {
  /** 文件或文件夹名称 */
  name: string
  /** 文件在工作空间中的相对路径 */
  path: string
  /** 是否为目录 */
  directory: boolean
  /** 文件全名（仅文件有效，含后缀） */
  fullName?: string
  /** 文件后缀（仅文件有效，不含点号） */
  extension?: string
  /** 文件大小可读格式（仅文件有效，如 "1.5 MB"） */
  readableSize?: string
  /** 最后修改时间（格式：yyyy-MM-dd HH:mm:ss） */
  lastModified?: string
  /** 最后修改时间戳（毫秒） */
  lastModifiedTime?: string
  /** 子节点（仅目录有效） */
  children?: WorkspaceFileNode[]
}

/**
 * 工作空间容量信息 VO
 */
export interface WorkspaceCapacityVO {
  /** 已使用空间（字节） */
  usedBytes: number
  /** 最大容量（字节） */
  maxBytes: number
  /** 已使用空间可读格式 */
  usedReadable: string
  /** 最大容量可读格式 */
  maxReadable: string
  /** 使用百分比（0~100） */
  percent: number
}

/**
 *  模型检查结果
 */
export interface CheckModelResult {
  success: boolean
  message: string
}

/** 服务状态信息 */
export interface ServiceStatusInfo {
  serviceType: 'FILE' | 'PROXY' | 'RUNTIME'
  status: 'UP' | 'DOWN'
  port: number | null
  lastHeartbeat: string
  startedAt: string
}

/** 执行节点状态 VO */
export interface NodeStatusVO {
  nodeId: string
  hostname: string
  ip: string
  firstSeenAt: string
  lastUpdatedAt: string
  services: ServiceStatusInfo[]
  nodeStatus: 'HEALTHY' | 'DEGRADED' | 'DOWN'
}

/** WebSocket 消息服务节点状态 VO */
export interface WebSocketNodeVO {
  nodeId: string
  hostname: string
  ip: string
  port: number | null
  status: 'UP' | 'DOWN'
  firstSeenAt: string
  lastUpdatedAt: string
  startedAt: string
  lastHeartbeat: string
}

/** 节点监控总览 VO：执行节点 + WebSocket 节点合一 */
export interface HeartbeatOverviewVO {
  nodes: NodeStatusVO[]
  websocketNodes: WebSocketNodeVO[]
}

/**
 * 对话显示名映射 VO：运行时内部标识 -> 中文显示名（对话渲染层专用，匿名会话可访问）
 */
export interface ChatDisplayNameVO {
  /** agentCode(小写) -> 智能体名称 */
  agents: Record<string, string>
  /** toolId -> 工具名称 */
  tools: Record<string, string>
  /** MCP toolName -> 「MCP服务名 · 工具名」 */
  mcpTools: Record<string, string>
}

/* ======================== 成本中心 ======================== */

/** 成本日趋势点 */
export interface CostTrendPoint {
  date: string
  inputCost: number
  outputCost: number
  cost: number
}

/** 按模型成本分布行 */
export interface CostModelRow {
  modelConfigId: string
  modelLabel: string
  inputTokens: number
  outputTokens: number
  cost: number
  unpricedTokens: number
  runCount: number | string
}

/** 智能体 TopN 行 */
export interface CostAgentRow {
  agentId: string
  agentName: string
  sessionCount: number | string
  runCount: number | string
  inputTokens: number
  outputTokens: number
  cost: number
  unpricedTokens: number
}

/** 成本概览（金额元、已计价口径；未配价用量单列） */
export interface CostOverviewVO {
  totalCost: number
  inputCost: number
  outputCost: number
  inputTokens: number | string
  outputTokens: number | string
  runCount: number | string
  sessionCount: number | string
  unpricedTokens: number | string
  trend: CostTrendPoint[]
  byModel: CostModelRow[]
  byBizType: { bizType: string; cost: number; inputTokens: number; outputTokens: number; runCount: number | string }[]
  /** 按渠道分布：channel null=渠道标记上线前的历史流水 */
  byChannel: { channel: string | null; cost: number; inputTokens: number; outputTokens: number; runCount: number | string }[]
  topAgents: CostAgentRow[]
  unpricedModels: CostModelRow[]
}

/** 会话账单行（后端按会话聚合的 Map） */
export interface CostSessionBillRow {
  sessionId: string
  title: string
  agentId: string
  agentName: string
  userId: string
  userName: string
  runCount: number | string
  /** 被重新生成顶替的废弃轮次数 */
  discardedRuns: number | string
  inputTokens: number
  outputTokens: number
  cost: number
  unpricedRuns: number | string
  /** 该会话用过的模型 label 逗号串 */
  models: string
  lastActiveAt: string
}

/** 会话明细的单轮条目 */
export interface CostRunItemVO {
  recordId: string
  messageId: number | null
  createdAt: string
  modelConfigId: string
  modelLabel: string
  iterationCount: number
  inputTokens: number
  outputTokens: number
  durationMs: number | null
  /** null=未计价 */
  cost: number | null
  /** false=被重新生成顶替的废弃分支 */
  onCurrentPath: boolean
  userQuestion: string | null
  assistantSummary: string | null
}

/** 成本中心-模型配价行（LLM 模型价格 + 近30天用量合并视图） */
export interface CostModelPricingRow {
  modelConfigId: string
  name: string
  modelId: string
  providerName: string | null
  providerType: string | null
  enabled: boolean
  inputPrice: number | null
  outputPrice: number | null
  tokens30d: number
  cost30d: number
  unpricedTokens30d: number
  runCount30d: number | string
}

/** 会话成本明细（实际发生口径：含废弃分支） */
export interface CostSessionDetailVO {
  sessionId: string
  title: string
  agentId: string
  agentName: string
  userId: string
  userName: string
  totalCost: number
  inputTokens: number
  outputTokens: number
  runCount: number
  discardedRunCount: number
  discardedCost: number
  unpricedRunCount: number
  byModel: { modelLabel: string; runCount: number; cost: number }[]
  runs: CostRunItemVO[]
}

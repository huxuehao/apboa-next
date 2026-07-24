// 扩展消息类型用于展示（含流式标记）
export interface DisplayMessage {
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool' | 'thinking'
  content: string
  createdAt?: string
  /** 消息元数据 JSON（durationMs/iterationCount/inputTokens/outputTokens/totalTokens），仅 assistant 正文有 */
  meta?: string
  isStreaming?: boolean
}

/**
 * HITL 确认字段元数据（后端注册 need_confirm 时归一登记，随 TOOL_CONFIRM_REQUIRED /
 * pending 下发）：确认 UI 据此渲染可编辑表单；空/缺失时降级为 JSON 只读展示。
 */
export interface ConfirmFieldMeta {
  /** 原始参数名（决策回传的 key，不做翻译） */
  name: string
  /** 参数类型：string / integer / number / boolean / array / object */
  type: string
  required?: boolean
  /** 参数描述（通用表单兜底层的展示辅助，来自工具 schema） */
  description?: string
  /** 枚举可选值（仅 MCP schema 声明 enum 时存在，渲染为下拉） */
  options?: string[]
}

/**
 * 子智能体过程步骤：实时（SUBAGENT_STEP 自定义事件）与落库（tool 消息 content.subProcess）
 * 结构同构，均由后端 RunTelemetryExtractor 生成（字段契约见 AguiCustomEvents.SUBAGENT_STEP）。
 * v1 旧数据（tool_use/tool_result 分开、内容截断）按普通步降级渲染
 */
export interface SubProcessStep {
  type: 'thinking' | 'text' | 'tool' | 'error' | 'tool_use' | 'tool_result'
  name?: string
  content?: string
  args?: string
  result?: string
  elapsed?: number
  /** 实时态：工具步已发起、结果未回（结果到达后清除） */
  running?: boolean
  /** 实时态：running 步在前端的到达时刻（毫秒），用于执行中耗时递增显示；落定时剔除 */
  startTime?: number
  /** 实时态：思考/回复步正在逐字流式生成（轮末完整步定稿时清除） */
  streaming?: boolean
  /** 实时态：子智能体内 HITL 待确认（挂起等待主会话决策，渲染允许/禁止按钮） */
  needConfirm?: boolean
  /** 实时态：待确认工具的参数字段元数据（确认表单渲染依据，随 pending 下发） */
  fields?: ConfirmFieldMeta[]
  /** 实时态：子确认已决策（允许/拒绝），等待续跑完成事件按 subToolUseId 配对；配对时清除 */
  decided?: boolean
  /** 子智能体内工具调用 id（工具完成事件配对 / 确认决策回传） */
  subToolUseId?: string
}

/** 工作流工具执行快照：执行中由节点事件增量组装，完成后由权威快照校准。 */
export interface WorkflowProcess {
  runId?: string
  workflowId?: string
  version?: string
  status?: 'RUNNING' | 'SUCCESS' | 'FAIL' | 'STOP' | string
  elapsed?: number
  error?: string
  nodes: WorkflowProcessNode[]
}

export interface WorkflowProcessNode {
  /** 单次节点执行标识；循环中同一 nodeId 会有多个 invocationId。 */
  invocationId?: string
  nodeId: string
  title?: string
  type?: string
  status?: 'RUNNING' | 'SUCCESS' | 'FAIL' | 'STOP' | string
  elapsed?: number
  inputs?: string
  outputs?: string
  error?: string
  modelName?: string
  modelRequests?: WorkflowModelRequest[]
}

export interface WorkflowModelRequest {
  requestIndex: number
  maxAttempts: number
  status?: 'SUCCESS' | 'FAIL' | 'STOP' | string
  durationMs?: number
  ttftMs?: number
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  modelTimeSeconds?: number
  finishReason?: string
  generateReason?: string
  thinkingChars?: number
  providerMetrics?: Record<string, string | number | boolean>
  attempts: WorkflowModelAttempt[]
  toolCalls?: WorkflowModelToolCall[]
}

export interface WorkflowModelAttempt {
  attempt: number
  status: 'SUCCESS' | 'FAIL' | 'STOP' | string
  elapsed?: number
  ttft?: number
  detail?: string
}

export interface WorkflowModelToolCall {
  id?: string
  name?: string
  arguments?: string
  status?: 'REQUESTED' | 'RUNNING' | 'SUCCESS' | 'FAIL' | 'SUSPENDED' | string
  elapsed?: number
  detail?: string
}

/** run 级元数据（RUN_META 事件载荷 / 落库 meta JSON 解析后），由后端 RunStatAccumulator 生成 */
export interface RunMeta {
  durationMs?: number
  iterationCount?: number
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  /** 本次回复实际使用的模型（消息级审计；多候选切换后可追溯） */
  modelConfigId?: string
  modelLabel?: string
}

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
}

/** run 级元数据（RUN_META 事件载荷 / 落库 meta JSON 解析后），由后端 RunStatAccumulator 生成 */
export interface RunMeta {
  durationMs?: number
  iterationCount?: number
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
}

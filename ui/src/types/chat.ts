// 扩展消息类型用于展示（含流式标记）
export interface DisplayMessage {
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool' | 'thinking' | 'subagent'
  content: string
  createdAt?: string
  isStreaming?: boolean
  /** 仅前端显示的上下文压缩提示，不会持久化。 */
  isMemoryCompression?: boolean
  subAgentRun?: import('./subagent').SubAgentRunVO | null
}

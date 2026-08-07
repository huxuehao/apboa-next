// 扩展消息类型用于展示（含流式标记）
export interface DisplayMessage {
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool' | 'thinking' | 'subagent'
  content: string
  createdAt?: string
  isStreaming?: boolean
  subAgentRun?: import('./subagent').SubAgentRunVO | null
}

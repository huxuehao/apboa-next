export type SubAgentTraceEventType =
  | 'STARTED'
  | 'MESSAGE_DELTA'
  | 'MESSAGE_COMPLETED'
  | 'TOOL_STARTED'
  | 'TOOL_ARGUMENTS'
  | 'TOOL_COMPLETED'
  | 'STATUS_CHANGED'
  | 'BLOCKED'
  | 'FAILED'
  | 'FINISHED'
  | 'CANCELLED'

export interface SubAgentTraceEvent {
  protocolVersion?: number
  eventId: string
  invocationId: string
  rootRunId?: string
  parentInvocationId?: string | null
  sequence: number
  occurredAt?: string
  eventType: SubAgentTraceEventType
  agent?: {
    definitionId?: string | number
    code?: string
    title?: string
    runtimeId?: string
    subagentSessionId?: string
  }
  payload?: Record<string, unknown>
}

export interface SubAgentRunVO {
  invocationId: string
  parentInvocationId?: string | null
  rootRunId?: string
  agentCode?: string
  agentTitle?: string
  subagentSessionId?: string
  status: 'RUNNING' | 'SUCCESS' | 'BLOCKED' | 'FAILED' | 'CANCELLED' | string
  task?: string
  summary?: string
  startedAt?: string
  endedAt?: string
  events: SubAgentTraceEvent[]
}

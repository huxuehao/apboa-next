import { computed, ref } from 'vue'
import type { CustomEvent, SubAgentRunVO, SubAgentTraceEvent } from '@/types'

const CUSTOM_EVENT_NAME = 'APBOA_SUBAGENT_EVENT'
const TERMINAL_STATUS = new Set(['SUCCESS', 'BLOCKED', 'FAILED', 'CANCELLED'])

function asTraceEvent(value: unknown): SubAgentTraceEvent | null {
  if (!value || typeof value !== 'object') return null
  const event = value as Partial<SubAgentTraceEvent>
  if (event.protocolVersion !== undefined && event.protocolVersion !== 1) return null
  if (!event.invocationId || !event.eventId || !event.eventType || event.sequence === undefined) return null
  return event as SubAgentTraceEvent
}

function statusFor(event: SubAgentTraceEvent): SubAgentRunVO['status'] {
  switch (event.eventType) {
    case 'FINISHED': return 'SUCCESS'
    case 'BLOCKED': return 'BLOCKED'
    case 'FAILED': return 'FAILED'
    case 'CANCELLED': return 'CANCELLED'
    default: return 'RUNNING'
  }
}

/**
 * Keeps the live custom-event protocol separate from the regular AG-UI message buffers.
 * An invocation id identifies one card; event ids and monotonically increasing sequence numbers
 * make reconnect replay and concurrent sibling agents idempotent.
 */
export function useSubAgentRuns() {
  const runsByInvocation = ref<Record<string, SubAgentRunVO>>({})
  const orderedInvocationIds = ref<string[]>([])

  const runs = computed(() => orderedInvocationIds.value
    .map((id) => runsByInvocation.value[id])
    .filter((run): run is SubAgentRunVO => Boolean(run)))

  function acceptCustomEvent(customEvent: CustomEvent): boolean {
    if (customEvent.name !== CUSTOM_EVENT_NAME) return false
    const event = asTraceEvent(customEvent.value)
    if (!event) return false
    accept(event)
    return true
  }

  function accept(event: SubAgentTraceEvent) {
    const existing = runsByInvocation.value[event.invocationId]
    const seen = existing?.events.some((item) => item.eventId === event.eventId
      || item.sequence === event.sequence) ?? false
    if (seen) return

    const payload = event.payload ?? {}
    const nextEvents = [...(existing?.events ?? []), event]
      .sort((left, right) => left.sequence - right.sequence)
    const nextStatus = statusFor(event)
    const terminal = TERMINAL_STATUS.has(nextStatus)
    const existingTerminal = Boolean(existing && TERMINAL_STATUS.has(existing.status))

    const next: SubAgentRunVO = {
      invocationId: event.invocationId,
      parentInvocationId: event.parentInvocationId ?? existing?.parentInvocationId,
      rootRunId: event.rootRunId ?? existing?.rootRunId,
      agentCode: event.agent?.code ?? existing?.agentCode,
      agentTitle: event.agent?.title ?? existing?.agentTitle,
      subagentSessionId: event.agent?.subagentSessionId ?? existing?.subagentSessionId,
      status: existingTerminal ? existing!.status : terminal ? nextStatus : existing?.status ?? nextStatus,
      task: typeof payload.task === 'string' ? payload.task : existing?.task,
      summary: typeof payload.summary === 'string' ? payload.summary : existing?.summary,
      startedAt: existing?.startedAt ?? event.occurredAt,
      endedAt: existingTerminal ? existing!.endedAt : terminal ? event.occurredAt : existing?.endedAt,
      events: nextEvents,
    }

    runsByInvocation.value = { ...runsByInvocation.value, [event.invocationId]: next }
    if (!existing) orderedInvocationIds.value = [...orderedInvocationIds.value, event.invocationId]
  }

  function reset() {
    runsByInvocation.value = {}
    orderedInvocationIds.value = []
  }

  return { runs, acceptCustomEvent, reset }
}

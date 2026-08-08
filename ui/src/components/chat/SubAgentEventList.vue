<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import MessageItem from './MessageItem.vue'
import ToolCallItem from './ToolCallItem.vue'
import type { SubAgentTraceEvent } from '@/types'
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'

const props = defineProps<{
  events: SubAgentTraceEvent[]
  active: boolean
}>()

defineEmits<{
  (e: 'inputTagPreview', value: unknown): void
  (e: 'interactionSubmit', payload: InteractionSubmitPayload): void
  (e: 'uipRetry', uipCode: string): void
  (e: 'vepRetry', vepCode: string): void
}>()

type TimelineItem =
  | {
      key: string
      kind: 'message'
      role: 'assistant' | 'thinking'
      content: string
      completed: boolean
    }
  | { key: string; kind: 'tool'; name: string; args?: unknown; result?: unknown; completed: boolean }
  | { key: string; kind: 'status'; content: string; failed: boolean }

const scrollRef = ref<HTMLElement | null>(null)
const followTail = ref(true)
let scrollRaf: number | null = null

const timeline = computed<TimelineItem[]>(() => {
  const items: TimelineItem[] = []
  const messages = new Map<string, Extract<TimelineItem, { kind: 'message' }>>()
  const tools = new Map<string, Extract<TimelineItem, { kind: 'tool' }>>()

  for (const event of [...props.events].sort((a, b) => a.sequence - b.sequence)) {
    const payload = event.payload ?? {}
    if (event.eventType === 'MESSAGE_DELTA' || event.eventType === 'MESSAGE_COMPLETED') {
      const role = payload.role === 'thinking' ? 'thinking' : 'assistant'
      const content = typeof payload.content === 'string' ? payload.content : ''
      const messageId = typeof payload.messageId === 'string' ? payload.messageId : event.eventId
      let item = messages.get(`${role}:${messageId}`)
      if (!item) {
        item = {
          key: `message:${role}:${messageId}`,
          kind: 'message',
          role,
          content: '',
          completed: false,
        }
        messages.set(`${role}:${messageId}`, item)
        items.push(item)
      }
      if (content && event.eventType === 'MESSAGE_DELTA') {
        item.content += content
      } else if (content) {
        // The terminal AgentScope chunk may carry either the full aggregate or a final delta.
        if (content.startsWith(item.content)) item.content = content
        else if (!item.content.startsWith(content)) item.content += content
      }
      // Completion belongs to one concrete message, not to the whole sub-agent card. This keeps
      // an earlier thinking panel from showing a spinner while a later reasoning round is active.
      if (event.eventType === 'MESSAGE_COMPLETED') item.completed = true
    } else if (event.eventType === 'TOOL_STARTED') {
      const toolCallId = typeof payload.toolCallId === 'string' ? payload.toolCallId : event.eventId
      const item: Extract<TimelineItem, { kind: 'tool' }> = {
        key: `tool:${toolCallId}`,
        kind: 'tool',
        name: typeof payload.name === 'string' ? payload.name : '工具调用',
        args: payload.args,
        completed: false,
      }
      tools.set(toolCallId, item)
      items.push(item)
    } else if (event.eventType === 'TOOL_ARGUMENTS') {
      const toolCallId = typeof payload.toolCallId === 'string' ? payload.toolCallId : ''
      const item = tools.get(toolCallId)
      if (item) item.args = payload.args ?? payload.delta
    } else if (event.eventType === 'TOOL_COMPLETED') {
      const toolCallId = typeof payload.toolCallId === 'string' ? payload.toolCallId : event.eventId
      const item = tools.get(toolCallId)
      if (item) {
        item.completed = true
        item.result = payload.result
      } else {
        items.push({
          key: `tool:${toolCallId}`,
          kind: 'tool',
          name: typeof payload.name === 'string' ? payload.name : '工具调用',
          result: payload.result,
          completed: true,
        })
      }
    } else if (event.eventType === 'BLOCKED' || event.eventType === 'FAILED' || event.eventType === 'CANCELLED') {
      items.push({
        key: `status:${event.eventId}`,
        kind: 'status',
        content: typeof payload.message === 'string'
          ? payload.message
          : event.eventType === 'BLOCKED' ? '子智能体执行被交互策略拦截'
            : event.eventType === 'CANCELLED' ? '子智能体已取消' : '子智能体运行失败',
        failed: true,
      })
    }
  }
  return items
})

function isNearBottom() {
  const el = scrollRef.value
  return !el || el.scrollHeight - el.scrollTop - el.clientHeight <= 20
}

function scrollToTail() {
  if (!followTail.value || scrollRaf !== null) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = null
    const el = scrollRef.value
    if (el && followTail.value) el.scrollTop = el.scrollHeight
  })
}

function onScroll() {
  followTail.value = isNearBottom()
}

function serialized(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value ?? '', null, 2)
}

function toolHistoryContent(item: Extract<TimelineItem, { kind: 'tool' }>): string {
  return JSON.stringify({
    name: item.name,
    totalTimes: 0,
    args: item.args === undefined ? '' : serialized(item.args),
    result: item.result === undefined ? '' : serialized(item.result),
  })
}

watch(() => props.events, () => nextTick(scrollToTail), { deep: true, flush: 'post' })
watch(() => props.active, (active) => { if (active && isNearBottom()) followTail.value = true })
onBeforeUnmount(() => { if (scrollRaf !== null) cancelAnimationFrame(scrollRaf) })
</script>

<template>
  <div ref="scrollRef" class="subagent-event-list" @scroll="onScroll">
    <div v-for="(item, index) in timeline" :key="item.key" class="subagent-event">
      <template v-if="item.kind === 'message'">
        <MessageItem
          :id="item.key"
          :current-index="index"
          :total-messages="timeline.length"
          :role="item.role"
          :content="item.content"
          :agent-has-result="true"
          :is-streaming="active && !item.completed"
          :readonly-interaction="true"
          @inputTagPreview="$emit('inputTagPreview', $event)"
        />
      </template>
      <template v-else-if="item.kind === 'tool'">
        <MessageItem
          v-if="item.completed"
          :id="item.key"
          :current-index="index"
          :total-messages="timeline.length"
          role="tool"
          :content="toolHistoryContent(item)"
          :agent-has-result="true"
        />
        <ToolCallItem
          v-else
          :id="item.key"
          :name="item.name"
          :args="item.args === undefined ? '' : serialized(item.args)"
          :loading="true"
        />
      </template>
      <template v-else>
        <MessageItem
          :id="item.key"
          :current-index="index"
          :total-messages="timeline.length"
          role="error"
          :content="item.content"
          :agent-has-result="true"
        />
      </template>
    </div>
    <div v-if="!timeline.length" class="subagent-event-empty">
      <LoadingOutlined v-if="active" spin />
      <span>{{ active ? '正在建立执行轨迹…' : '没有可展示的执行明细' }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.subagent-event-list { max-height: 320px; overflow-y: auto; overscroll-behavior: contain; padding: 2px; }
.subagent-event + .subagent-event { margin-top: 6px; }
.subagent-event-empty { display: flex; align-items: center; gap: 8px; min-height: 48px; padding: 0 10px; color: #98a2b3; font-size: 13px; }
</style>

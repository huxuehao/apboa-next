<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { BulbOutlined, CheckCircleOutlined, LoadingOutlined, ToolOutlined } from '@ant-design/icons-vue'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import type { SubAgentTraceEvent } from '@/types'

const props = defineProps<{
  events: SubAgentTraceEvent[]
  active: boolean
}>()

type TimelineItem =
  | { key: string; kind: 'message'; role: 'assistant' | 'thinking'; content: string }
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
        item = { key: `message:${role}:${messageId}`, kind: 'message', role, content: '' }
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
    } else if (event.eventType === 'FAILED' || event.eventType === 'CANCELLED') {
      items.push({
        key: `status:${event.eventId}`,
        kind: 'status',
        content: typeof payload.message === 'string'
          ? payload.message
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

watch(() => props.events, () => nextTick(scrollToTail), { deep: true, flush: 'post' })
watch(() => props.active, (active) => { if (active && isNearBottom()) followTail.value = true })
onBeforeUnmount(() => { if (scrollRaf !== null) cancelAnimationFrame(scrollRaf) })
</script>

<template>
  <div ref="scrollRef" class="subagent-event-list" @scroll="onScroll">
    <div v-for="item in timeline" :key="item.key" class="subagent-event" :class="`subagent-event--${item.kind}`">
      <template v-if="item.kind === 'message'">
        <BulbOutlined v-if="item.role === 'thinking'" class="subagent-event-icon" />
        <CheckCircleOutlined v-else class="subagent-event-icon" />
        <div>
          <div class="subagent-event-label">{{ item.role === 'thinking' ? '思考过程' : '执行输出' }}</div>
          <MarkdownRenderer
            v-if="item.role === 'assistant' && item.content"
            class="subagent-event-markdown"
            :content="item.content"
            :is-streaming="active"
          />
          <div v-else class="subagent-event-content">{{ item.content || '…' }}</div>
        </div>
      </template>
      <template v-else-if="item.kind === 'tool'">
        <ToolOutlined class="subagent-event-icon" />
        <div class="subagent-event-body">
          <div class="subagent-event-label">
            {{ item.completed ? '已完成工具调用' : '正在调用工具' }}：{{ item.name }}
            <LoadingOutlined v-if="!item.completed" spin class="subagent-inline-loading" />
          </div>
          <pre v-if="item.args !== undefined" class="subagent-event-code">{{ typeof item.args === 'string' ? item.args : JSON.stringify(item.args, null, 2) }}</pre>
          <pre v-if="item.result !== undefined" class="subagent-event-code">{{ typeof item.result === 'string' ? item.result : JSON.stringify(item.result, null, 2) }}</pre>
        </div>
      </template>
      <template v-else>
        <div class="subagent-event-status" :class="{ 'is-failed': item.failed }">{{ item.content }}</div>
      </template>
    </div>
    <div v-if="!timeline.length" class="subagent-event-empty">
      <LoadingOutlined v-if="active" spin />
      <span>{{ active ? '正在建立执行轨迹…' : '没有可展示的执行明细' }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.subagent-event-list { max-height: 320px; overflow-y: auto; overscroll-behavior: contain; padding: 4px 2px 6px; }
.subagent-event { display: flex; gap: 8px; padding: 9px 10px; border-radius: 8px; color: #4b5563; }
.subagent-event + .subagent-event { margin-top: 4px; }
.subagent-event--message { background: #f8fafc; }
.subagent-event--tool { background: #f7faff; }
.subagent-event-icon { flex: 0 0 auto; margin-top: 3px; color: #1677ff; }
.subagent-event-body { min-width: 0; flex: 1; }
.subagent-event-label { color: #667085; font-size: 12px; line-height: 20px; }
.subagent-event-content { white-space: pre-wrap; overflow-wrap: anywhere; color: #344054; font-size: 13px; line-height: 1.65; }
.subagent-event-markdown { color: #344054; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
.subagent-event-code { max-height: 120px; margin: 6px 0 0; overflow: auto; padding: 7px 8px; border-radius: 6px; background: #eef2f7; color: #475467; font-size: 12px; line-height: 1.45; white-space: pre-wrap; overflow-wrap: anywhere; }
.subagent-inline-loading { margin-left: 5px; color: #1677ff; }
.subagent-event-status { width: 100%; padding: 6px 8px; border-left: 3px solid #98a2b3; color: #667085; font-size: 13px; }
.subagent-event-status.is-failed { border-color: #ff4d4f; color: #cf1322; background: #fff1f0; }
.subagent-event-empty { display: flex; align-items: center; gap: 8px; min-height: 48px; padding: 0 10px; color: #98a2b3; font-size: 13px; }
</style>

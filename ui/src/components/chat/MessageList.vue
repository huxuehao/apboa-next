<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  BulbOutlined,
  CheckCircleOutlined,
  DownOutlined,
  LoadingOutlined
} from '@ant-design/icons-vue'
import MessageItem from './MessageItem.vue'
import ToolCallItem from './ToolCallItem.vue'
import type { DisplayMessage } from '@/types'
import type {FlatFileItem} from "@/composables/chat/useWorkspaceFiles.ts";
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'

const props = defineProps<{
  messages: DisplayMessage[]
  agentHasResult?: boolean
  toolCalls: Array<{ id: string; name: string; args: string; result?: string; elapsed?: number, needConfirm?: boolean }>
}>()

defineEmits<{
  (e: 'toolContent', value: any): void
  (e: 'inputTagPreview', value: FlatFileItem): void
  (e: 'interactionSubmit', payload: InteractionSubmitPayload): void
  (e: 'uipRetry', uipCode: string): void
  (e: 'vepRetry', vepCode: string): void
}>()

/** 消息分组 */
interface MessageGroup {
  text: boolean
  key: string
  isStreaming: boolean
  messages: DisplayMessage[]
}

/**
 * 将消息列表按规则分组：连续 ≥2 条 thinking/tool 且后接 assistant 时，
 * 聚合成一个“行为聚合”组；否则每条独立展示。
 * 流式末尾未闭合的 run 不会被聚合。
 */
const messageGroups = computed<MessageGroup[]>(() => {
  const groups: MessageGroup[] = []
  const messages_ = props.messages

  for (let i = 0; i < messages_.length; i++) {
    const msg: DisplayMessage = messages_[i]!
    if (msg.role === 'thinking' || msg.role === 'tool') {
      if (groups.length > 0) {
        const lastGroup = groups[groups.length - 1]!
        if (!lastGroup.text) {
          lastGroup.messages.push(msg)
          lastGroup.isStreaming = msg.isStreaming || false
        } else {
          groups.push({
            text: false,
            key: msg.id,
            isStreaming: msg.isStreaming || false,
            messages: [msg],
          })
        }
      } else {
        groups.push({
          text: false,
          key: msg.id,
          isStreaming: msg.isStreaming || false,
          messages: [msg],
        })
      }
    } else {
      groups.push({
        text: true,
        key: msg.id,
        isStreaming: false,
        messages: [msg],
      })
    }
  }

  return groups
})

/** 聚合组展开状态 */
const expandedMap = ref<Record<string, boolean>>({})

function isExpanded(key: string): boolean {
  return expandedMap.value[key]!
}

/** 提取分组首条消息（single 组保证有且只有一条） */
function firstMessage(group: MessageGroup): DisplayMessage {
  return group.messages[0]!
}

function toggleAggregate(key: string) {
  expandedMap.value = {
    ...expandedMap.value,
    [key]: !expandedMap.value[key],
  }
}
</script>

<template>
  <div class="chat-main-messages">
    <template v-for="(group, gIdx) in messageGroups" :key="group.key">
      <!-- 行为聚合组 -->
      <div v-if="group.messages.length > 1" class="chat-aggregate-panel">
        <div class="chat-aggregate-header" @click="toggleAggregate(group.key)">
          <span class="chat-aggregate-icon">
            <LoadingOutlined v-if="group.isStreaming" spin />
            <CheckCircleOutlined v-else />
          </span>
          <span class="chat-aggregate-title">行为聚合 ({{ group.messages.length }})</span>
          <span class="chat-aggregate-arrow" :class="{ expanded: isExpanded(group.key) }">
            <DownOutlined />
          </span>
        </div>
        <div class="chat-aggregate-body" :class="{ 'is-expanded': isExpanded(group.key) }">
          <MessageItem
            v-for="msg in group.messages"
            :key="msg.id"
            :id="msg.id"
            :current-index="0"
            :total-messages="0"
            :role="msg.role"
            :content="msg.content"
            :created-at="msg.createdAt"
            :agent-has-result="agentHasResult"
            :is-streaming="msg.isStreaming"
            @inputTagPreview="$emit('inputTagPreview', $event as FlatFileItem)"
            @interaction-submit="$emit('interactionSubmit', $event)"
            @uip-retry="$emit('uipRetry', $event)"
            @vep-retry="$emit('vepRetry', $event)"
          />
        </div>
      </div>
      <!-- 单条消息 -->
      <MessageItem
        v-else
        :key="firstMessage(group).id"
        :id="firstMessage(group).id"
        :current-index="gIdx"
        :total-messages="messageGroups.length"
        :role="firstMessage(group).role"
        :content="firstMessage(group).content"
        :created-at="firstMessage(group).createdAt"
        :agent-has-result="agentHasResult"
        :is-streaming="firstMessage(group).isStreaming"
        @inputTagPreview="$emit('inputTagPreview', $event as FlatFileItem)"
        @interaction-submit="$emit('interactionSubmit', $event)"
        @uip-retry="$emit('uipRetry', $event)"
        @vep-retry="$emit('vepRetry', $event)"
      />
    </template>
    <TransitionGroup name="jelly">
      <ToolCallItem
        v-for="t in toolCalls"
        :key="t.id"
        :id="t.id"
        :name="t.name"
        :args="t.args"
        :result="t.result"
        :elapsed="t.elapsed"
        :loading="t.result == null"
        :need-confirm="t.needConfirm"
        @toolContent="(content: any) => $emit('toolContent', content)"
      />
    </TransitionGroup>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

/** 微果冻动画：ToolCallItem 出现/消失 */
.jelly-enter-active {
  animation: jelly-enter 0.35s cubic-bezier(0.34, 1.3, 0.64, 1) both;
}

.jelly-leave-active {
  animation: jelly-leave 0.2s ease-out both;
}

.jelly-move {
  transition: transform 0.3s ease;
}

@keyframes jelly-enter {
  0% {
    opacity: 0;
    transform: translateY(3px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes jelly-leave {
  0% {
    opacity: 1;
    transform: scale(0.99);
  }
  100% {
    opacity: 0;
    transform: scale(0.98);
  }
}
</style>

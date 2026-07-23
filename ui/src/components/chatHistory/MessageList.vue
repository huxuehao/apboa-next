<script setup lang="ts">
import { computed } from 'vue'
import MessageItem from './MessageItem.vue'
import DayDivider from '../chat/DayDivider.vue'
import type { DisplayMessage } from '@/types'

const props = defineProps<{
  messages: DisplayMessage[]
}>()

/**
 * 日期分隔：createdAt 的日期（前 10 位）与上一条不同处标记 dividerDay（与聊天页一致）。
 * 无时间的消息不参与比较，避免误插。
 */
const wrappedMessages = computed(() => {
  let prevDay: string | null = null
  return props.messages.map((msg) => {
    const day = (msg.createdAt || '').slice(0, 10)
    let dividerDay: string | null = null
    if (day.length === 10 && day !== prevDay) {
      dividerDay = day
      prevDay = day
    }
    return { msg, dividerDay }
  })
})
</script>

<template>
  <div class="chat-main-messages">
    <template v-for="item in wrappedMessages" :key="item.msg.id">
      <DayDivider v-if="item.dividerDay" :day="item.dividerDay" />
      <MessageItem
        :role="item.msg.role"
        :content="item.msg.content"
        :created-at="item.msg.createdAt"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;
</style>

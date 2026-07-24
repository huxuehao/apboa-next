<script setup lang="ts">
import { computed, ref, nextTick } from 'vue'
import type { DisplayMessage } from '@/types'

const FILE_SEP = '@==##::::##==@'

const props = defineProps<{
  messages: DisplayMessage[]
  scrollContainer: HTMLElement | null
}>()

/**
 * 从用户消息中提取纯文本（去除文件前缀）
 */
function extractUserText(content: string): string {
  // 去除富文本标签
  const t = content.replace(/<\/?(?:workspace-file|agent-tool|agent-skill|agent-mcp)>/g, '')
  // 去除文件前缀
  const idx = t.indexOf(FILE_SEP)
  if (idx === -1) return t.trim()
  return t.slice(idx + FILE_SEP.length).trim()
}

/**
 * 过滤用户消息并提取预览文本
 */
const userMessages = computed(() =>
  props.messages
    .filter(m => m.role === 'user')
    .map(m => ({
      id: m.id,
      text: extractUserText(m.content) || '(附件)',
    })),
)

/**
 * 用户消息 >= 2 条时显示导航器
 */
const isVisible = computed(() => userMessages.value.length >= 2)

const isHovered = ref(false)
const activeMessageId = ref<string | null>(null)

/**
 * 滚动到指定用户消息并短暂高亮
 */
function scrollToMessage(id: string) {
  if (!props.scrollContainer) return
  const target = props.scrollContainer.querySelector(`[data-msg-id="${id}"]`)
  if (!target) return

  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  activeMessageId.value = id

  nextTick(() => {
    const el = target as HTMLElement
    el.classList.add('msg-highlight')
    setTimeout(() => {
      el.classList.remove('msg-highlight')
      activeMessageId.value = null
    }, 1500)
  })
}
</script>

<template>
  <div
    v-if="isVisible"
    class="message-navigator"
    @mouseenter="isHovered = true"
    @mouseleave="isHovered = false"
  >
    <!-- 收起态：圆点指示器 -->
    <div class="navigator-track">
      <div
        v-for="(msg, idx) in userMessages"
        :key="msg.id"
        class="navigator-dot"
        :class="{ 'is-active': activeMessageId === msg.id }"
        :title="`消息 ${idx + 1}`"
        @click.stop="scrollToMessage(msg.id)"
      />
    </div>
    <!-- 展开态：消息预览面板 -->
    <div class="navigator-panel" :class="{ 'is-open': isHovered }">
      <div class="navigator-header">
        <span class="navigator-title">消息索引</span>
        <span class="navigator-count">{{ userMessages.length }}</span>
      </div>
      <div class="navigator-list">
        <div
          v-for="(msg, idx) in userMessages"
          :key="msg.id"
          class="navigator-item"
          :class="{ 'is-active': activeMessageId === msg.id }"
          @click.stop="scrollToMessage(msg.id)"
        >
          <span class="navigator-item-index">{{ idx + 1 }}</span>
          <span class="navigator-item-text" :title="msg.text">{{ msg.text }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

.message-navigator {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  max-height: 80%;
}

/* 圆点轨道 */
.navigator-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 3px;
  overflow-y: auto;
  max-height: 100%;
  flex-shrink: 0;

  &::-webkit-scrollbar {
    width: 0;
  }
}

.navigator-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background-color: #c0c4cc;
  cursor: pointer;
  flex-shrink: 0;
  opacity: 0.5;
  transition: all 0.2s ease;

  &:hover {
    opacity: 1;
    background-color: $chat-primary;
    transform: scale(1.6);
  }

  &.is-active {
    opacity: 1;
    background-color: $chat-primary;
    transform: scale(1.6);
  }
}

/* 展开面板 */
.navigator-panel {
  width: 0;
  opacity: 0;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08), 0 1px 4px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              opacity 0.2s ease;
  flex-shrink: 0;

  &.is-open {
    width: 300px;
    opacity: 1;
  }
}

.navigator-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  white-space: nowrap;
}

.navigator-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.navigator-count {
  font-size: 11px;
  color: #909399;
  background: rgba(0, 0, 0, 0.04);
  padding: 1px 8px;
  border-radius: 10px;
}

.navigator-list {
  max-height: 400px;
  overflow-y: auto;
  padding: 4px 0;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 2px;
  }
}

.navigator-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.15s ease;

  &:hover {
    background-color: rgba($chat-primary, 0.06);
  }

  &.is-active {
    background-color: rgba($chat-primary, 0.1);
  }
}

.navigator-item-index {
  font-size: 11px;
  color: #b0b4bb;
  min-width: 18px;
  text-align: center;
  flex-shrink: 0;
}

.navigator-item-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--color-text-regular);
}

/* 移动端隐藏 */
@media (max-width: $mobile-breakpoint) {
  .message-navigator {
    display: none;
  }
}
</style>

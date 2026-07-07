<script setup lang="ts">
import GuideTrigger from './GuideTrigger.vue'
import { useGuide } from './useGuide'
import type { GuideEntry } from './types'

const props = defineProps<{
  /** 指南条目列表（由注册中心提供） */
  entries: GuideEntry[]
  /** 弹窗标题 */
  title?: string
  /** 弹窗宽度 */
  width?: string
}>()

const { open, activeId, selectGuide } = useGuide(props.entries)
</script>

<template>
  <GuideTrigger @click="open = true" />

  <AModal
    v-model:open="open"
    :title="title ?? '使用说明'"
    :width="width ?? '820px'"
    :footer="null"
    :destroy-on-close="false"
  >
    <div class="guide-layout">
      <!-- 左侧导航 -->
      <nav class="guide-sidebar">
        <button
          v-for="entry in entries"
          :key="entry.id"
          :class="['sidebar-item', { active: activeId === entry.id }]"
          type="button"
          @click="selectGuide(entry.id)"
        >
          {{ entry.title }}
        </button>
      </nav>

      <!-- 右侧内容区：动态渲染当前指南内容组件 -->
      <div class="guide-content">
        <component
          :is="entries.find((e) => e.id === activeId)?.component"
        />
      </div>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.guide-layout {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 0;
  min-height: 420px;
}

.guide-sidebar {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 16px;
  border-right: 1px solid #f0f0f0;
}

.sidebar-item {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 14px;
  color: #595959;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;

  &:hover {
    background: #f5f5f5;
    color: #262626;
  }

  &.active {
    background: #f0f5ff;
    color: #1677ff;
    font-weight: 600;
  }
}

.guide-content {
  min-width: 0;
  padding-left: 24px;
  max-height: 60vh;
  overflow-y: auto;
}
</style>

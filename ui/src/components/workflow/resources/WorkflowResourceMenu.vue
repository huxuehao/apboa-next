<script setup lang="ts">
import type { WorkflowResourceKind, WorkflowResourceSummary } from '@/types/workflowResources'

defineProps<{
  modelValue: WorkflowResourceKind
  summary: WorkflowResourceSummary
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: WorkflowResourceKind): void
}>()

const menuItems: Array<{ key: WorkflowResourceKind; label: string; countKey: keyof WorkflowResourceSummary }> = [
  { key: 'datasource', label: '数据源', countKey: 'datasourceTotal' },
  { key: 'cache', label: 'Redis缓存', countKey: 'cacheTotal' },
  { key: 'mq', label: '消息队列', countKey: 'mqTotal' },
  { key: 'channel', label: '通知渠道', countKey: 'channelTotal' }
]
</script>

<template>
  <div class="settings-menu">
    <div class="settings-menu-title">资源维护</div>
    <div class="settings-menu-list">
      <div
        v-for="item in menuItems"
        :key="item.key"
        class="settings-menu-item workflow-resource-menu-item"
        :class="{ active: modelValue === item.key }"
        @click="emit('update:modelValue', item.key)"
      >
        <span class="settings-menu-label">{{ item.label }}</span>
        <span class="workflow-resource-menu-count">{{ summary[item.countKey] || 0 }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/_settings.scss' as *;

.workflow-resource-menu-item {
  justify-content: flex-start;
}

.workflow-resource-menu-count {
  margin-left: auto;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--color-bg-secondary, #f5f5f5);
  color: var(--color-text-secondary, #666);
  font-size: 12px;
}

.settings-menu-item.active .workflow-resource-menu-count {
  background: rgba(22, 119, 255, 0.1);
  color: var(--color-primary);
}
</style>

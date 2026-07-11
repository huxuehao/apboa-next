<script setup lang="ts">
import type { WorkflowOutputConfig } from '@/types/workflow'

defineProps<{
  outputs: WorkflowOutputConfig[]
}>()
</script>

<template>
  <div class="output-list">
    <div v-for="output in outputs" :key="output.name" class="output-item">
      <span class="output-name" :title="output.name">{{ output.name }}</span>
      <span class="output-sep">&ensp;·&ensp;</span>
      <span class="output-type-tag" :class="`type-${(output.type || 'Object').toLowerCase()}`">{{ output.type || 'Object' }}</span>
      <span class="output-desc" :title="output.description || '节点输出'">{{ output.description || '节点输出' }}</span>
    </div>
    <div v-if="!outputs.length" class="output-empty">暂无输出配置</div>
  </div>
</template>

<style scoped lang="scss">
.output-list {
  display: grid;
  gap: 8px;
}

.output-item {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 8px;
  background: #F2F4F7;
  transition: border-color 0.2s ease;
  min-width: 0;
}

.output-name {
  color: #262626;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 0 1 auto;
  max-width: 35%;
  min-width: 0;
}

.output-sep {
  flex-shrink: 0;
  white-space: nowrap;
  color: #bfbfbf;
}

.output-type-tag {
  flex-shrink: 0;
  white-space: nowrap;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #2f54eb;

  &.type-object { color: #2f54eb; }
  &.type-string {  color: #52c41a; }
  &.type-integer, &.type-long, &.type-float, &.type-double { color: #fa8c16; }
  &.type-boolean {  color: #eb2f96; }
  &.type-array {  color: #722ed1; }
}

.output-desc {
  color: #8c8c8c;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-left: auto;
  max-width: 45%;
  text-align: right;
}

.output-empty {
  padding: 14px;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  color: #bfbfbf;
  font-size: 12px;
  text-align: center;
}
</style>

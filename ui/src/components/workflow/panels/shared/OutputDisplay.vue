<script setup lang="ts">
import type { WorkflowOutputConfig } from '@/types/workflow'

defineProps<{
  outputs: WorkflowOutputConfig[]
}>()
</script>

<template>
  <div class="output-list">
    <div v-for="output in outputs" :key="output.name" class="output-item">
      <div class="output-head">
        <span class="output-name">{{ output.name }}: </span>
        <span class="output-type-tag" :class="`type-${(output.type || 'Object').toLowerCase()}`">{{ output.type || 'Object' }}</span>
      </div>
      <span class="output-desc">{{ output.description || '节点运行输出' }}</span>
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
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  border-radius: 8px;
  background: #F2F4F7;
  transition: border-color 0.2s ease;
}

.output-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.output-name {
  color: #262626;
  font-size: 13px;
  font-weight: 700;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.output-type-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: #f0f5ff;
  color: #2f54eb;

  &.type-object { background: #f0f5ff; color: #2f54eb; }
  &.type-string { background: #f6ffed; color: #52c41a; }
  &.type-integer, &.type-long, &.type-float, &.type-double { background: #fff7e6; color: #fa8c16; }
  &.type-boolean { background: #fff0f6; color: #eb2f96; }
  &.type-array { background: #f9f0ff; color: #722ed1; }
}

.output-desc {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
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

<script setup lang="ts">
import { PlusOutlined } from '@ant-design/icons-vue'

defineEmits<{
  create: []
}>()
</script>

<template>
  <button type="button" class="workflow-create-card" @click="$emit('create')">
    <PlusOutlined class="create-icon" />
    <span class="create-title">新建工作流</span>
    <span class="create-desc">编排节点、配置输入输出并发布为可执行流程</span>
  </button>
</template>

<style scoped lang="scss">
.workflow-create-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  padding: var(--spacing-md);
  border: 2px dotted var(--color-border-base);
  border-radius: var(--border-radius-lg);
  background: var(--color-bg-white);
  color: var(--color-text-secondary);
  cursor: pointer;
  text-align: center;
}

.workflow-create-card:hover {
  border-color: var(--color-primary);
  background: var(--color-bg-light);
  color: var(--color-primary);
}

.create-icon {
  margin-bottom: var(--spacing-sm);
  font-size: 30px;
}

.create-title {
  margin-bottom: var(--spacing-xs);
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
}

.create-desc {
  max-width: 220px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-sm);
  line-height: 1.5;
}
</style>

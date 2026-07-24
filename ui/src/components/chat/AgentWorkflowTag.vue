<script setup lang="ts">
/**
 * Agent 工作流标签消息渲染组件
 * 用于在消息流中渲染 <agent-workflow>workflowName</agent-workflow> 内容
 * （工作流名即 WorkflowTool 的 LLM 调用名，同时就是展示名，无需映射）
 *
 * @component
 */
import { ApartmentOutlined } from '@ant-design/icons-vue'

defineProps<{
  /** 工作流名（标签内容 = LLM 调用名 = 展示名） */
  content: string
}>()
</script>

<template>
  <span class="agent-workflow-tag" :title="content">
    <ApartmentOutlined class="agent-workflow-tag-icon" />
    <span class="agent-workflow-tag-name">{{ content }}</span>
  </span>
</template>

<style scoped lang="scss">
.agent-workflow-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  background: rgba(250, 140, 22, 0.12);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.4;
  color: #FA8C16;
  vertical-align: middle;
  white-space: nowrap;
  user-select: none;
}

.agent-workflow-tag-icon {
  flex-shrink: 0;
  font-size: 14px;
  opacity: 0.9;
}

.agent-workflow-tag-name {
  flex-shrink: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
</style>

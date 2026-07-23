<script setup lang="ts">
/**
 * Agent MCP 工具标签消息渲染组件
 * 用于在消息流中渲染 <agent-mcp>toolName</agent-mcp> 内容
 *
 * @component
 */
import { computed } from 'vue'
import { ApiOutlined } from '@ant-design/icons-vue'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'

const props = defineProps<{
  /** MCP 工具名（标签内容 = LLM 调用名，发送给 agent 用） */
  content: string
}>()

const { resolveToolCallName } = useToolCallDisplayName()

/** 展示文本：复用工具调用显示名映射（MCP toolName → 「MCP服务名 · 工具名」），未命中回退原值 */
const display = computed(() => resolveToolCallName(props.content))
</script>

<template>
  <span class="agent-mcp-tag" :title="content">
    <ApiOutlined class="agent-mcp-tag-icon" />
    <span class="agent-mcp-tag-name">{{ display }}</span>
  </span>
</template>

<style scoped lang="scss">
.agent-mcp-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  background: rgba(19, 194, 194, 0.12);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.4;
  color: #13C2C2;
  vertical-align: middle;
  white-space: nowrap;
  user-select: none;
}

.agent-mcp-tag-icon {
  flex-shrink: 0;
  font-size: 14px;
  opacity: 0.9;
}

.agent-mcp-tag-name {
  flex-shrink: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
</style>

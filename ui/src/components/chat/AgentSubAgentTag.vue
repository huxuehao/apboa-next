<script setup lang="ts">
/**
 * Agent 子智能体标签消息渲染组件
 * 用于在消息流中渲染 <agent-sub-agent>agentCode</agent-sub-agent> 内容
 *
 * @component
 */
import { computed } from 'vue'
import { RobotOutlined } from '@ant-design/icons-vue'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'

const props = defineProps<{
  /** 子智能体 agentCode 小写（标签内容 = Agent-as-Tool 的 LLM 调用名） */
  content: string
}>()

const { resolveToolCallName } = useToolCallDisplayName()

/** 展示文本：复用工具调用显示名映射（agentCode → 智能体名称），未命中回退原值 */
const display = computed(() => resolveToolCallName(props.content))
</script>

<template>
  <span class="agent-sub-agent-tag" :title="content">
    <RobotOutlined class="agent-sub-agent-tag-icon" />
    <span class="agent-sub-agent-tag-name">{{ display }}</span>
  </span>
</template>

<style scoped lang="scss">
.agent-sub-agent-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  background: rgba(114, 46, 209, 0.10);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.4;
  color: #722ED1;
  vertical-align: middle;
  white-space: nowrap;
  user-select: none;
}

.agent-sub-agent-tag-icon {
  flex-shrink: 0;
  font-size: 14px;
  opacity: 0.9;
}

.agent-sub-agent-tag-name {
  flex-shrink: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
</style>

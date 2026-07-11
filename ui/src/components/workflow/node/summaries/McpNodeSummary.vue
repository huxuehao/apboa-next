<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const mcpServerName = computed(() => {
  const name = props.config.mcpServerName
  return name ? String(name) : null
})

const mcpToolName = computed(() => {
  const name = props.config.mcpToolName
  return name ? String(name) : null
})
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodemcp'" :icon-color="schema.color">
      MCP: {{ mcpServerName || '未选择' }}
    </SummaryRow>
    <SummaryRow>
      工具: {{ mcpToolName || '未选择' }}
    </SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

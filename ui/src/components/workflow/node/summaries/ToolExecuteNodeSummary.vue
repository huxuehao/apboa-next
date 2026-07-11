<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const toolName = computed(() => {
  const name = props.config.toolName
  return name ? String(name) : null
})
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodetool'" :icon-color="schema.color">
      工具: {{ toolName || '未选择' }}
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

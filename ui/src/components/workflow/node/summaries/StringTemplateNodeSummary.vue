<script setup lang="ts">
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const templateType = (props.config.templateType as string) || 'STRING'
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodestring_template'" :icon-color="schema.color">模板: {{ templateType === 'VELOCITY' ? 'Velocity' : '字符串' }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

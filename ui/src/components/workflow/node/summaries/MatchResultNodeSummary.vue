<script setup lang="ts">
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { matchTypeLabels } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const count = Array.isArray(props.config.matches) ? props.config.matches.length : 0
const matchLabel = matchTypeLabels[(props.config.matchType as string) || 'EQUALS'] || '等于'
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodematch_result'" :icon-color="schema.color">{{ count }} 个匹配项</SummaryRow>
    <SummaryRow>匹配方式: {{ matchLabel }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

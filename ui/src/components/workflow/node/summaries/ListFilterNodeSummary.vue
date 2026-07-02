<script setup lang="ts">
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { filterModeLabels } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const modeLabel = filterModeLabels[(props.config.mode as string) || 'SIMPLE'] || '简单'
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodelist_filter'" :icon-color="schema.color">模式: {{ modeLabel }}</SummaryRow>
    <SummaryRow v-if="config.condition"><code>{{ config.condition }}</code></SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

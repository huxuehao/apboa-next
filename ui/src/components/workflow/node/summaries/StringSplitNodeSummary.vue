<script setup lang="ts">
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { splitModeLabels } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const modeLabel = splitModeLabels[(props.config.mode as string) || 'SIMPLE'] || '简单分隔符'
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodestring_split'" :icon-color="schema.color">模式: {{ modeLabel }}</SummaryRow>
    <SummaryRow v-if="config.delimiter"><code>{{ config.delimiter }}</code></SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

<script setup lang="ts">
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { directionLabels } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const dirLabel = directionLabels[(props.config.direction as string) || 'ASC'] || '升序'
const strictText = props.config.strictMode ? '是' : '否'
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodelist_sort'" :icon-color="schema.color">方向: {{ dirLabel }}</SummaryRow>
    <SummaryRow>严格模式: {{ strictText }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

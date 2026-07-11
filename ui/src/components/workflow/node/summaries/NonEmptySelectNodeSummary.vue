<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { strategyLabels } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const label = computed(() => strategyLabels[(props.config.strategy as string) || 'FIRST'] || '第一个')
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodenon_empty_select'" :icon-color="schema.color">策略: {{ label }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

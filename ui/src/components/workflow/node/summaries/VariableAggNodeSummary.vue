<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { aggStrategyLabels } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const label = computed(() => aggStrategyLabels[(props.config.strategy as string) || 'MAP'] || 'Map')
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodevariable_agg'" :icon-color="schema.color">策略: {{ label }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

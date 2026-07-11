<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { modeLabels } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const format = computed(() => (props.config.format as string) || 'JSON')
const modeLabel = computed(() => modeLabels[(props.config.mode as string) || 'COMPACT'] || '紧凑')
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodeserialize'" :icon-color="schema.color">格式: {{ format }} / {{ modeLabel }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

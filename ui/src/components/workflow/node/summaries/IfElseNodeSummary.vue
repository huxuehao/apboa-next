<script setup lang="ts">
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const symbol = (props.config.symbol as string) || '?'
const scopeLabel = (props.config.scope as string) === 'LENGTH' ? '长度' : '值本身'
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodeif_else'" :icon-color="schema.color">运算符: {{ symbol }}</SummaryRow>
    <SummaryRow>计算对象: {{ scopeLabel }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

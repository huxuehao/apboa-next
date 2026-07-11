<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const maxIterations = computed(() => props.config.maxIterations || 1000)
const itemVariable = computed(() => (props.config.itemVariable as string) || 'item')
</script>

<template>
  <div class="node-summary">
    <SummaryRow>最多 {{ maxIterations }} 次</SummaryRow>
    <SummaryRow>元素变量: {{ itemVariable }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

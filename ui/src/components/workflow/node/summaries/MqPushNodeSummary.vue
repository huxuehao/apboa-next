<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { resourceName } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const mqName = computed(() => resourceName(props.resources?.mqs || [], props.config.mqId))
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodemq_push'" :icon-color="schema.color">MQ: {{ mqName }}</SummaryRow>
    <SummaryRow>主题: <code>{{ config.topicOrQueue || '未设置' }}</code></SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

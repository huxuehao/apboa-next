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

const dsName = computed(() => resourceName(props.resources?.datasources || [], props.config.datasourceId))
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodedb_delete'" :icon-color="schema.color">数据源: {{ dsName }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

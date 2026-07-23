<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const hasModel = computed(() => Boolean(props.config.modelConfigId))
const intentCount = computed(() => {
  const intents = props.config.intents as Array<Record<string, unknown>> | undefined
  return intents?.length || 0
})
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="schema.icon" :icon-color="schema.color">
      模型: {{ hasModel ? '已选择' : '未选择' }}
    </SummaryRow>
    <SummaryRow>
      意图数: {{ intentCount }}
    </SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

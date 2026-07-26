<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

// 表达式取首行展示，过长由 code 样式截断
const expressionText = computed(() => {
  const expression = String(props.config.expression || '').trim()
  return expression ? expression.split('\n')[0] : ''
})
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodebusiness'" :icon-color="schema.color">
      <template v-if="expressionText">表达式: <code>{{ expressionText }}</code></template>
      <template v-else>未设置表达式</template>
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

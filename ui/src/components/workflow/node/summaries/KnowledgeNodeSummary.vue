<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const knowledgeBaseName = computed(() => {
  const name = props.config.knowledgeBaseName
  return name ? String(name) : null
})
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodeknowledge_base'" :icon-color="schema.color">
      知识库: {{ knowledgeBaseName || '未选择' }}
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

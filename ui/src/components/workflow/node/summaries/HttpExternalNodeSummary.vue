<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const request = computed(() => (props.config.request || {}) as Record<string, unknown>)
const method = computed(() => String(request.value.method || 'GET'))
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodehttp_inline'" :icon-color="schema.color">请求方法: {{ method }}</SummaryRow>
    <SummaryRow><code>{{ request.url || '未设置请求URL' }}</code></SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

<script setup lang="ts">
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import { resourceName } from './summaryUtils'
import SummaryRow from './SummaryRow.vue'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const cacheName = resourceName(props.resources?.caches || [], props.config.cacheId)
const expireText = props.config.expire ? `${props.config.expire}s` : '不过期'
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodecache'" :icon-color="schema.color">实例: {{ cacheName }}</SummaryRow>
    <SummaryRow>KEY: <code>{{ config.key || '未设置' }}</code></SummaryRow>
    <SummaryRow>TTL: {{ expireText }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'
import { resourceName } from './summaryUtils'

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

const channelLabel = computed(() =>
  resourceName(props.resources?.channels || [], props.config.channelId),
)

const toPreview = computed(() => {
  const raw = String(props.config.toRecipients || '')
  return raw.length > 30 ? raw.slice(0, 30) + '...' : raw
})
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="schema.icon" :icon-color="schema.color">
      渠道: {{ channelLabel || '未设置' }}
    </SummaryRow>
    <SummaryRow>
      收件人: {{ toPreview || '未设置' }}
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

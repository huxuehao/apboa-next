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
const skillCount = computed(() => countArray(props.config.skillPackageIds))
const toolCount = computed(() => countArray(props.config.toolIds))
const mcpCount = computed(() => countArray(props.config.mcps))

function countArray(value: unknown) {
  return Array.isArray(value) ? value.length : 0
}
</script>

<template>
  <div class="node-summary">
    <SummaryRow :icon="'nodellm'" :icon-color="schema.color">
      模型: {{ hasModel ? '已选择' : '未选择' }}
    </SummaryRow>
    <SummaryRow>
      技能 {{ skillCount }} / 工具 {{ toolCount }} / MCP {{ mcpCount }}
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

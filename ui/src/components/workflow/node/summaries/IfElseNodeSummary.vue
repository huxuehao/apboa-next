<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeSchema, WorkflowResourceMaps } from '@/types/workflow'
import SummaryRow from './SummaryRow.vue'

interface BranchLike {
  symbol?: string
  conditionExpression?: string
}

const props = defineProps<{
  config: Record<string, unknown>
  resources?: WorkflowResourceMaps
  schema: WorkflowNodeSchema
}>()

// 兼容旧版平铺配置：无 branches 时按单条件展示
const branches = computed<BranchLike[]>(() => {
  const raw = props.config.branches
  if (Array.isArray(raw) && raw.length > 0) return raw as BranchLike[]
  if (props.config.symbol) {
    return [{ symbol: props.config.symbol as string, conditionExpression: props.config.conditionExpression as string }]
  }
  return []
})

const maxVisible = 3
const visibleBranches = computed(() => branches.value.slice(0, maxVisible))
const hiddenCount = computed(() => Math.max(0, branches.value.length - maxVisible))

function branchLabel(index: number) {
  return index === 0 ? 'IF' : 'ELSE IF'
}

const elseConfigured = computed(() => {
  const value = props.config.elseNextNodeId || props.config.falseNextNodeId
  return Boolean(value)
})
</script>

<template>
  <div class="node-summary">
    <SummaryRow
      v-for="(branch, index) in visibleBranches"
      :key="index"
      :icon="index === 0 ? 'nodeif_else' : undefined"
      :icon-color="index === 0 ? schema.color : undefined"
    >
      {{ branchLabel(index) }}
    </SummaryRow>
    <SummaryRow v-if="hiddenCount > 0">等 {{ branches.length }} 个分支</SummaryRow>
    <SummaryRow>ELSE{{ elseConfigured ? '' : ': 未配置' }}</SummaryRow>
  </div>
</template>

<style scoped lang="scss">
.node-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>

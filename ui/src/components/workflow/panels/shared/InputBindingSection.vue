<script setup lang="ts">
import InputBindingEditor from '@/components/workflow/bindings/InputBindingEditor.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowInputConfig } from '@/types/workflow'

defineProps<{
  modelValue?: WorkflowInputConfig[]
  nodes: WorkflowFlowNode[]
  edges?: WorkflowFlowEdge[]
  currentNodeId?: string
  maxBindings?: number
  readonlyName?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: WorkflowInputConfig[]]
}>()
</script>

<template>
  <InputBindingEditor
      :model-value="modelValue"
      :nodes="nodes"
      :edges="edges || []"
      :current-node-id="currentNodeId"
      :max-bindings="maxBindings"
      :readonly-name="readonlyName"
      @update:model-value="(value) => emit('update:modelValue', value)"
    />
</template>

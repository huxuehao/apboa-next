<script setup lang="ts">
import { computed } from 'vue'
import BlurInput from '@/components/workflow/panels/shared/BlurInput.vue'
import type { WorkflowFlowNode } from '@/types/workflow'

const props = defineProps<{
  modelValue?: unknown
  nodes: WorkflowFlowNode[]
  currentNodeId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

const value = computed<Record<string, unknown>>(() => {
  if (props.modelValue && typeof props.modelValue === 'object') return props.modelValue as Record<string, unknown>
  return { type: 'CONSTANT', value: '' }
})

const nodeOptions = computed(() =>
  props.nodes
    .filter((node) => node.id !== props.currentNodeId)
    .map((node) => ({ label: `${node.data.label} (${node.data.type})`, value: node.id })),
)

function update(key: string, nextValue: unknown) {
  emit('update:modelValue', { ...value.value, [key]: nextValue })
}
</script>

<template>
  <div class="compare-editor">
    <ASegmented
      :value="value.type || 'CONSTANT'"
      :options="[
        { label: '常量', value: 'CONSTANT' },
        { label: '节点输出', value: 'VARIABLE' },
      ]"
      @update:value="(next: string) => update('type', next)"
    />

    <template v-if="value.type === 'VARIABLE'">
      <ASelect
        show-search
        :value="value.sourceNodeId as string"
        :options="nodeOptions"
        placeholder="选择来源节点"
        @update:value="(next: string) => update('sourceNodeId', next)"
      />
      <BlurInput
        :model-value="String(value.value ?? 'output')"
        placeholder="输出名，默认 output"
        @update:model-value="(next: string) => update('value', next)"
      />
    </template>

    <BlurInput
      v-else
      :model-value="String(value.value ?? '')"
      placeholder="请输入比较值"
      @update:model-value="(next: string) => update('value', next)"
    />
  </div>
</template>

<style scoped lang="scss">
.compare-editor {
  display: grid;
  gap: 8px;
}
</style>

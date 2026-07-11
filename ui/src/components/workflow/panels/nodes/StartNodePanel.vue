<script setup lang="ts">
import { computed } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import WorkflowArrayEditors from '@/components/workflow/fields/WorkflowArrayEditors.vue'
import type { WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{
  update: [node: WorkflowFlowNode]
}>()

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}

function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

const variableTypeOptions = [
  'String',
  'Long',
  'Integer',
  'Float',
  'Double',
  'Boolean',
  'Array',
  'Object',
].map((v) => ({ label: v, value: v }))

const derivedOutputs = computed(() => {
  const params = (props.node.data.config?.params as Array<{ name: string; type: string; description?: string }>) || []
  return params.map((p) => ({
    name: p.name,
    type: p.type,
    description: p.description || '',
  }))
})
</script>

<template>
  <AForm layout="vertical">
    <PanelSection title="节点名称">
      <NodeNameInput
        :model-value="node.data.label"
        @update:model-value="(value: string) => updateNode({ label: value })"
      />
    </PanelSection>

    <PanelSection title="节点配置">
      <WorkflowArrayEditors
        :model-value="node.data.config?.params"
        type="startParams"
        :options="variableTypeOptions"
        @update:model-value="(value) => updateConfig('params', value)"
      />
    </PanelSection>

    <PanelSection title="输出说明">
      <OutputDisplay :outputs="derivedOutputs" />
    </PanelSection>
  </AForm>
</template>

<style scoped lang="scss">
.config-desc {
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f6f8fa;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;
}
</style>

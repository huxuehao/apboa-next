<script setup lang="ts">
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import WorkflowResourceSelect from '@/components/workflow/fields/WorkflowResourceSelect.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}
</script>

<template>
  <AForm layout="vertical">
    <PanelSection title="节点名称">
      <NodeNameInput
        :model-value="node.data.label"
        @update:model-value="(v: any) => updateNode({ label: v })"
      />
    </PanelSection>
    <InputBindingSection
      :model-value="node.data.inputConfigs"
      :nodes="nodes"
      :edges="edges"
      :current-node-id="node.id"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <AFormItem label="缓存实例" required>
        <WorkflowResourceSelect
          :model-value="String(node.data.config?.cacheId || '')"
          resource-type="cache"
          :resources="resources"
          @update:model-value="(v: any) => updateConfig('cacheId', v)"
        />
      </AFormItem>
      <AFormItem>
        <template #label>
          <span>模板格式&nbsp;</span>
          <FormatterGuideModal />
        </template>
        <ASelect
          :value="node.data.config?.formatterType || 'VELOCITY'"
          :options="[
            { label: '普通字符串', value: 'STRING' },
            { label: 'Jackson JSON', value: 'JACKSON' },
            { label: 'Velocity 模板', value: 'VELOCITY' },
          ]"
          @update:value="(v: any) => updateConfig('formatterType', v)"
        />
      </AFormItem>
      <AFormItem label="缓存键" required>
        <BlurInput
          :model-value="String(node.data.config?.key || '')"
          placeholder="例如 user:${userId}"
          @update:model-value="(v: any) => updateConfig('key', v)"
        />
      </AFormItem>
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
</template>

<style scoped lang="scss">
.field-help {
  display: block;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}
</style>

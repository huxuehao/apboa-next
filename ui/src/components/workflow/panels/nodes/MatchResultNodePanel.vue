<script setup lang="ts">
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import NextNodeSelector from '@/components/workflow/bindings/NextNodeSelector.vue'
import AutoInputBinding from '@/components/workflow/bindings/AutoInputBinding.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import AutoMatchBinding from '@/components/workflow/bindings/AutoMatchBinding.vue'
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
    <AutoInputBinding
      :model-value="node.data.inputConfigs"
      :nodes="nodes"
      :edges="edges"
      :current-node-id="node.id"
      :draggable="false"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <AutoMatchBinding
      :model-value="(node.data.config?.matches as any) || []"
      :nodes="nodes"
      :edges="edges"
      :current-node-id="node.id"
      @update:model-value="(v: any) => updateConfig('matches', v)"
    />
    <PanelSection title="节点配置">
      <div class="config-row">
        <span class="config-row-label">匹配方式</span>
        <ASegmented
          :value="node.data.config?.matchType || 'EQUALS'"
          :options="[
            { label: '等于', value: 'EQUALS' },
            { label: '包含', value: 'CONTAINS' },
          ]"
          @update:value="(v: any) => updateConfig('matchType', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">区分大小写</span>
        <ASwitch
          :checked="Boolean(node.data.config?.caseSensitive ?? true)"
          @update:checked="(v: any) => updateConfig('caseSensitive', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label" style="margin-right: 70px;">默认输出</span>
        <div class="prev-node-selector">
          <NextNodeSelector
            :nodes="nodes"
            :edges="edges"
            :current-node-id="node.id"
            :selected-node-id="(node.data.config?.defaultNextNodeId as string) || undefined"
            @select="(id: string) => updateConfig('defaultNextNodeId', id)"
            @clear="updateConfig('defaultNextNodeId', undefined)"
          />
        </div>
      </div>
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
</template>

<style scoped lang="scss">
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.config-row-label {
  flex-shrink: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
}

.prev-node-selector {
  flex: 1;
}
</style>

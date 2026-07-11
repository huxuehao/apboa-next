<script setup lang="ts">
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import AutoInputBinding from '@/components/workflow/bindings/AutoInputBinding.vue'
import PrevNodeSelector from '@/components/workflow/bindings/PrevNodeSelector.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
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
      :draggable="true"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <div class="config-desc">从多个候选输入中选择第一个或最后一个非空值。</div>
      <div class="config-row">
        <span class="config-row-label">
          选择策略
          <ATooltip title="第一个：按输入绑定顺序返回第一个非空值。最后一个：遍历所有输入，返回最后一个非空值">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASegmented
          :value="node.data.config?.strategy || 'FIRST'"
          :options="[
            { label: '第一个', value: 'FIRST' },
            { label: '最后一个', value: 'LAST' },
          ]"
          @update:value="(v: any) => updateConfig('strategy', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label" style="margin-right: 70px;">
          默认选择
          <ATooltip title="当所有输入绑定的值都为空时，路由到此处选择的默认节点">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <div class="prev-node-selector">
          <PrevNodeSelector
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
.config-desc {
  margin-bottom: 12px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;
}

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
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  color: rgba(0, 0, 0, 0.25);
  font-size: 13px;
  cursor: help;

  &:hover {
    color: rgba(0, 0, 0, 0.45);
  }
}

.prev-node-selector {
  flex: 1;
  min-width: 0;
}
</style>

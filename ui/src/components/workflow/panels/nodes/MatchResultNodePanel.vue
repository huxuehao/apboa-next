<script setup lang="ts">
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
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
      title="输入节点（第一个有效）"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <div class="config-row">
        <span class="config-row-label">
          <ASelect
            :value="node.data.config?.matchType || 'EQUALS'"
            :options="[
              { label: '等于', value: 'EQUALS' },
              { label: '包含', value: 'CONTAINS' },
            ]"
            style="width: 70px; margin-right: 5px;"
            @update:value="(v: any) => updateConfig('matchType', v)"
          />
        </span>
        <div class="match-binding-wrap">
        <AutoMatchBinding
          :model-value="(node.data.config?.matches as any) || []"
          :nodes="nodes"
          :edges="edges"
          :current-node-id="node.id"
          @update:model-value="(v: any) => updateConfig('matches', v)"
        />
        </div>
      </div>
      <div class="config-row">
        <span class="config-row-label">
          区分大小写
          <ATooltip title="是否区分大小写进行匹配：开启后精确匹配字符大小写，关闭后忽略大小写">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASwitch
          :checked="Boolean(node.data.config?.caseSensitive ?? true)"
          @update:checked="(v: any) => updateConfig('caseSensitive', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label" style="margin-right: 120px;">
          默认输出
          <ATooltip title="当所有匹配条件都不满足时，路由到此处选择的默认节点">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
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

.match-binding-wrap {
  flex: 1;
  min-width: 0;
}
</style>

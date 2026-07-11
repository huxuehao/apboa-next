<script setup lang="ts">
import { computed } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
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

const isString = computed(() => (props.node.data.config?.strategy as string) === 'STRING')
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
      <div class="config-row">
        <span class="config-row-label">
          聚合策略
          <ATooltip title="数组：所有输入值按顺序放入数组。Map：以「绑定名→值」的键值对形式输出。字符串：用拼接符将所有值连成一个字符串">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASegmented
          :value="node.data.config?.strategy || 'MAP'"
          :options="[
            { label: '数组', value: 'ARRAY' },
            { label: 'Map', value: 'MAP' },
            { label: '字符串', value: 'STRING' },
          ]"
          @update:value="(v: any) => updateConfig('strategy', v)"
        />
      </div>
      <div v-if="isString" class="config-row">
        <span class="config-row-label">字符串拼接符</span>
        <BlurInput
          :model-value="String(node.data.config?.splicingSymbol || '')"
          style="width: 172px"
          @update:model-value="(v: any) => updateConfig('splicingSymbol', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          排除空值
          <ATooltip title="是否在聚合时过滤值为 null 或空的输入绑定，开启后仅聚合有值的输入">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASwitch
          :checked="Boolean(node.data.config?.excludeNull)"
          @update:checked="(v: any) => updateConfig('excludeNull', v)"
        />
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
</style>

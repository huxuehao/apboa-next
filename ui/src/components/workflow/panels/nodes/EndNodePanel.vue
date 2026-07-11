<script setup lang="ts">
import { ref } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{
  update: [node: WorkflowFlowNode]
}>()

const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)

function onEditorMaximizeChange(val: boolean) {
  isEditorMaximized.value = val
}

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}

function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

const formatterOptions = [
  { label: '普通字符串', value: 'STRING' },
  { label: 'Jackson JSON', value: 'JACKSON' },
  { label: 'Velocity 模板', value: 'VELOCITY' },
]
</script>

<template>
  <div ref="panelRoot" class="end-panel" :class="{ 'editor-maximized': isEditorMaximized }">
  <AForm layout="vertical">
    <PanelSection title="节点名称">
      <NodeNameInput
        :model-value="node.data.label"
        @update:model-value="(value: string) => updateNode({ label: value })"
      />
    </PanelSection>

    <InputBindingSection
      :model-value="node.data.inputConfigs"
      :nodes="nodes"
      :edges="edges"
      :current-node-id="node.id"
      :max-bindings="1"
      :readonly-name="true"
      @update:model-value="(value) => updateNode({ inputConfigs: value })"
    />

    <PanelSection title="节点配置">
      <ConfigCodeEditor
        :model-value="String(node.data.config?.responseTemplate ?? '${input}')"
        language="txt"
        placeholder="如 ${input}，支持 Velocity 变量"
        :maximize-target="panelRoot"
        @update:model-value="(value: string) => updateConfig('responseTemplate', value)"
        @maximize-change="onEditorMaximizeChange"
      />
      <span class="field-help">使用 ${输入绑定名} 引用输入绑定。</span>

      <div class="config-row">
        <span class="config-row-label">响应模板格式 <FormatterGuideModal /></span>
        <ASelect
          :value="node.data.config?.formatterType || 'STRING'"
          :options="formatterOptions"
          style="width: 160px"
          @update:value="(value: string) => updateConfig('formatterType', value)"
        />
      </div>
    </PanelSection>

    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.end-panel {
  position: relative;

  &.editor-maximized {
    height: 100%;
    overflow: hidden;
  }
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-top: 16px;
}

.config-row-label {
  flex-shrink: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
}

.field-help {
  display: block;
  margin-top: 4px;
  margin-bottom: 8px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}
</style>

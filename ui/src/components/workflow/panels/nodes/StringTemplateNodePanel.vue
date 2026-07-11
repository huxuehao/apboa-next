<script setup lang="ts">
import { ref } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import type { WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

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
</script>

<template>
  <div ref="panelRoot" class="template-panel" :class="{ 'editor-maximized': isEditorMaximized }">
  <AForm layout="vertical">
    <PanelSection title="节点名称"
      ><NodeNameInput
        :model-value="node.data.label"
        @update:model-value="(v: any) => updateNode({ label: v })"
    /></PanelSection>
    <InputBindingSection
      :model-value="node.data.inputConfigs"
      :nodes="nodes"
      :current-node-id="node.id"
      :max-bindings="1"
      :readonly-name="true"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <div class="config-row">
        <span class="config-row-label">模板格式</span>
        <div class="formatter-selector">
          <FormatterGuideModal />
          <ASelect
            :value="node.data.config?.templateType || 'STRING'"
            :options="[
              { label: '字符串', value: 'STRING' },
              { label: 'Velocity', value: 'VELOCITY' },
            ]"
            style="width: 130px"
            @update:value="(v: any) => updateConfig('templateType', v)"
          />
        </div>
      </div>
      <ConfigCodeEditor
        :model-value="String(node.data.config?.template || '')"
        language="txt"
        placeholder="输入模板内容，Velocity 模式使用 ${变量} 语法"
        :maximize-target="panelRoot"
        @update:model-value="(v: any) => updateConfig('template', v)"
        @maximize-change="onEditorMaximizeChange"
      />
    </PanelSection>
    <PanelSection title="输出说明"
      ><OutputDisplay :outputs="node.data.outputConfigs || []"
    /></PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.template-panel {
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
  margin-bottom: 12px;
}

.config-row-label {
  flex-shrink: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
}

.formatter-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>

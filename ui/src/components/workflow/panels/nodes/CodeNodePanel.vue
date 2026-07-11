<script setup lang="ts">
import { ref, computed } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
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
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)

function onEditorMaximizeChange(val: boolean) {
  isEditorMaximized.value = val
}

const languageOptions = [
  { label: 'Java', value: 'JAVA' },
  { label: 'Python', value: 'PYTHON', disabled: true },
  { label: 'JavaScript', value: 'JAVASCRIPT', disabled: true },
]

const codeLanguage = computed(() => {
  switch (props.node.data.config?.language) {
    case 'JAVASCRIPT':
      return 'javascript' as const
    default:
      return 'java' as const
  }
})

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}
</script>

<template>
  <div ref="panelRoot" class="code-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
          <span class="config-row-label">语言</span>
          <ASelect
            :value="node.data.config?.language || 'JAVA'"
            :options="languageOptions"
            style="width: 130px"
            @update:value="(v: any) => updateConfig('language', v)"
          />
        </div>
        <ConfigCodeEditor
          :model-value="String(node.data.config?.codeSource || '')"
          :language="codeLanguage"
          placeholder="编写处理逻辑..."
          :maximize-target="panelRoot"
          @update:model-value="(v: any) => updateConfig('codeSource', v)"
          @maximize-change="onEditorMaximizeChange"
        />
      </PanelSection>
      <PanelSection title="输出说明">
        <OutputDisplay :outputs="node.data.outputConfigs || []" />
      </PanelSection>
    </AForm>
  </div>
</template>

<style scoped lang="scss">
.code-panel {
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
</style>


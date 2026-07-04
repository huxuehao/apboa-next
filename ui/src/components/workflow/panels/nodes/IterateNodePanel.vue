<script setup lang="ts">
import { ref, computed } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
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

const languageOptions = [
  { label: 'Java', value: 'JAVA' },
]

const codeLanguage = computed(() => {
  switch (props.node.data.config?.language) {
    case 'PYTHON':
      return 'python' as const
    case 'JAVASCRIPT':
      return 'javascript' as const
    case 'SQL':
      return 'sql' as const
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
  <div ref="panelRoot" class="iterate-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
      :current-node-id="node.id"
      :max-bindings="1"
      :readonly-name="true"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <template #actions>
        <ASelect
          :value="node.data.config?.language || 'JAVA'"
          :options="languageOptions"
          size="small"
          style="width: 100px"
          @update:value="(v: any) => updateConfig('language', v)"
        />
      </template>
      <p class="config-desc">对集合输入逐项执行迭代处理代码。</p>
      <ConfigCodeEditor
        :model-value="String(node.data.config?.iterateCode || '')"
        :language="codeLanguage"
        placeholder="编写迭代处理逻辑..."
        :maximize-target="panelRoot"
        @update:model-value="(v: any) => updateConfig('iterateCode', v)"
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
.iterate-panel {
  position: relative;
  
  &.editor-maximized {
    height: 100%;
    overflow: hidden;
  }
}

.config-desc {
  margin: 0 0 12px;
  padding: 0;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;
  background: none;
  border-radius: 0;
}
</style>

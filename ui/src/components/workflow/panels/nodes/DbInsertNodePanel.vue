<script setup lang="ts">
import { ref } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import WorkflowResourceSelect from '@/components/workflow/fields/WorkflowResourceSelect.vue'
import WorkflowArrayEditors from '@/components/workflow/fields/WorkflowArrayEditors.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import type { WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)

function onEditorMaximizeChange(val: boolean) {
  isEditorMaximized.value = val
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

const dbParamTypeOptions = [
  'STRING',
  'INTEGER',
  'INT',
  'LONG',
  'DOUBLE',
  'FLOAT',
  'BOOLEAN',
  'BOOL',
].map((v: any) => ({ label: v, value: v }))
const formatterOptions = [
  { label: '纯文本替换', value: 'STRING' },
  { label: 'JSON 保类型', value: 'JACKSON' },
  { label: 'Velocity 模板', value: 'VELOCITY' },
]
</script>

<template>
  <div ref="panelRoot" class="db-insert-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <AFormItem label="数据源" required>
        <WorkflowResourceSelect
          :model-value="String(node.data.config?.datasourceId || '')"
          resource-type="datasource"
          :resources="resources"
          @update:model-value="(v: any) => updateConfig('datasourceId', v)"
        />
      </AFormItem>
      <div class="sql-editor-field">
        <label class="form-label">SQL 语句 <span class="required-mark">*</span></label>
        <ConfigCodeEditor
          :model-value="String(node.data.config?.sql || '')"
          language="sql"
          placeholder="使用 ? 作为参数占位符"
          :maximize-target="panelRoot"
          @update:model-value="(v: any) => updateConfig('sql', v)"
          @maximize-change="onEditorMaximizeChange"
        />
        <span class="field-help">SQL 中的 ? 占位符与下方参数按顺序一一对应。</span>
      </div>
      <div class="sql-params-field">
        <div class="params-header">
          <span class="form-label">参数绑定</span>
          <div class="formatter-selector">
            <FormatterGuideModal />
            <ASelect
              :value="node.data.config?.formatterType || 'VELOCITY'"
              :options="formatterOptions"
              size="small"
              style="width: 130px"
              @update:value="(v: any) => updateConfig('formatterType', v)"
            />
          </div>
        </div>
        <WorkflowArrayEditors
          :model-value="node.data.config?.params"
          type="dbParams"
          :options="dbParamTypeOptions"
          @update:model-value="(v: any) => updateConfig('params', v)"
        />
        <span class="field-help">
          参数值会按所选格式渲染后传入 SQL。
        </span>
      </div>
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.db-insert-panel {
  position: relative;
  
  &.editor-maximized {
    height: 100%;
    overflow: hidden;
  }
}

.sql-editor-field {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.5;
}

.required-mark {
  color: #ff4d4f;
  margin-left: 2px;
}

.field-help {
  display: block;
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}
.sql-params-field {
  margin-bottom: 24px;
}

.params-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;

  .form-label {
    margin-bottom: 0;
  }
}

.formatter-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>

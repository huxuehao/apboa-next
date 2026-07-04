<script setup lang="ts">
import { ref } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import WorkflowResourceSelect from '@/components/workflow/fields/WorkflowResourceSelect.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)

function onEditorMaximizeChange(val: boolean) {
  isEditorMaximized.value = val
}

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

function stringify(v: unknown) {
  return v === undefined || v === null ? '' : typeof v === 'string' ? v : JSON.stringify(v, null, 2)
}
</script>

<template>
  <div ref="panelRoot" class="cache-set-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
      <div class="cache-key-field">
        <div class="key-header">
          <span class="key-label">缓存键 <span class="required-mark">*</span></span>
          <div class="formatter-selector">
            <FormatterGuideModal />
            <ASelect
              :value="node.data.config?.formatterType || 'VELOCITY'"
              :options="[
                { label: '纯文本替换', value: 'STRING' },
                { label: 'JSON 保类型', value: 'JACKSON' },
                { label: 'Velocity 模板', value: 'VELOCITY' },
              ]"
              size="small"
              style="width: 130px"
              @update:value="(v: any) => updateConfig('formatterType', v)"
            />
          </div>
        </div>
        <BlurInput
          :model-value="String(node.data.config?.key || '')"
          placeholder="例如 user:${userId}"
          @update:model-value="(v: any) => updateConfig('key', v)"
        />
        <span class="field-help">使用 ${变量名} 引用输入绑定，模板格式控制变量替换方式。</span>
      </div>
      <div class="cache-editor-field">
        <label class="form-label">缓存值 <span class="required-mark">*</span></label>
        <ConfigCodeEditor
          :model-value="stringify(node.data.config?.value)"
          language="txt"
          placeholder="支持字符串、数字、对象或数组。"
          :maximize-target="panelRoot"
          @update:model-value="
            (v: string) => {
              try {
                updateConfig('value', JSON.parse(v))
              } catch {
                updateConfig('value', v)
              }
            }"
          @maximize-change="onEditorMaximizeChange"
        />
      </div>
      <AFormItem label="过期时间(秒)">
        <AInputNumber
          class="full-input"
          :value="Number(node.data.config?.expire ?? 0)"
          :min="0"
          placeholder="0 表示不过期"
          @update:value="(v: any) => updateConfig('expire', v ?? 0)"
        />
      </AFormItem>
    </PanelSection>

    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.cache-set-panel {
  position: relative;
  
  &.editor-maximized {
    height: 100%;
    overflow: hidden;
  }
}

.cache-key-field {
  margin-bottom: 24px;
}

.key-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.key-label {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  line-height: 1.5;
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

.formatter-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cache-editor-field {
  margin-bottom: 24px;
}

.field-help {
  display: block;
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}

.full-input {
  width: 100%;
}
</style>

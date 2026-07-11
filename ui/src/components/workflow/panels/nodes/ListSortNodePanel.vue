<script setup lang="ts">
import { ref } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
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

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}
</script>

<template>
  <div ref="panelRoot" class="sort-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
      :max-bindings="1"
      :readonly-name="true"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <div class="config-row">
        <span class="config-row-label">
          求值引擎
          <ATooltip title="当排序字段为 Groovy 表达式时使用的脚本引擎">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASelect
          :value="node.data.config?.evaluatorType || 'GROOVY'"
          :options="[{ label: 'Groovy', value: 'GROOVY' }]"
          style="width: 160px"
          @update:value="(v: any) => updateConfig('evaluatorType', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          排序字段
          <ATooltip title="排序依据，支持三种写法：① item（元素本身需为数字）② item.age（按属性排序）③ Groovy 表达式，如 item.price * item.quantity">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
      </div>
      <ConfigCodeEditor
        :model-value="String(node.data.config?.condition || '')"
        language="txt"
        placeholder="item、item.age 或 Groovy 表达式"
        :maximize-target="panelRoot"
        @update:model-value="(v: any) => updateConfig('condition', v)"
        @maximize-change="onEditorMaximizeChange"
      />
      <div class="config-row">
        <span class="config-row-label">排序方向</span>
        <ASegmented
          :value="node.data.config?.direction || 'ASC'"
          :options="[
            { label: '升序', value: 'ASC' },
            { label: '降序', value: 'DESC' },
          ]"
          @update:value="(v: any) => updateConfig('direction', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          空值靠前
          <ATooltip title="开启后，无法排序的元素（null、非数字等）排在前面；关闭则排在末尾">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASwitch
          :checked="Boolean(node.data.config?.nullFirst)"
          @update:checked="(v: any) => updateConfig('nullFirst', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          严格模式
          <ATooltip title="开启后，遇到无法排序的元素直接报错中断；关闭则将其按空值处理（排到前/后）">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASwitch
          :checked="Boolean(node.data.config?.strictMode)"
          @update:checked="(v: any) => updateConfig('strictMode', v)"
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
.sort-panel {
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

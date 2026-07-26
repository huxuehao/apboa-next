<script setup lang="ts">
import { computed, inject, ref, type Ref } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import type {
  WorkflowFlowEdge,
  WorkflowFlowNode,
  WorkflowResourceMaps,
  WorkflowVariable,
} from '@/types/workflow'

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

// ========== 可用变量 ==========

const customVariables = inject<Ref<WorkflowVariable[]>>('workflowVariables', ref([]))

// 内置变量，由引擎在运行时自动注入
const builtinVariables = ['tenantId', 'tenantCode', 'userId', 'userName']

// 输入绑定的参数名即表达式中的变量名
const inputVarNames = computed(() =>
  (props.node.data.inputConfigs || []).map((item) => item.name).filter(Boolean),
)
const customVarNames = computed(() => customVariables.value.map((item) => item.name))

// 点击变量标签追加到表达式末尾
function appendVariable(name: string) {
  const expr = String(props.node.data.config?.expression || '')
  updateConfig('expression', expr ? `${expr}${/\s$/.test(expr) ? '' : ' '}${name}` : name)
}
</script>

<template>
  <div ref="panelRoot" class="constant-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
          求值引擎
          <ATooltip title="计算表达式使用的脚本引擎">
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
          可用变量
          <ATooltip title="表达式中可直接使用的变量，点击标签插入到表达式末尾。同名时输入绑定优先于全局变量">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
      </div>
      <div class="variable-groups">
        <div v-if="inputVarNames.length" class="variable-group">
          <span class="variable-group-label">输入绑定</span>
          <div class="variable-tags">
            <ATag
              v-for="name in inputVarNames"
              :key="`input-${name}`"
              :bordered="false"
              class="variable-tag"
              @click="appendVariable(name)"
            >{{ name }}</ATag>
          </div>
        </div>
        <div v-if="customVarNames.length" class="variable-group">
          <span class="variable-group-label">全局变量</span>
          <div class="variable-tags">
            <ATag
              v-for="name in customVarNames"
              :key="`custom-${name}`"
              :bordered="false"
              class="variable-tag"
              @click="appendVariable(name)"
            >{{ name }}</ATag>
          </div>
        </div>
        <div class="variable-group">
          <span class="variable-group-label">内置变量</span>
          <div class="variable-tags">
            <ATag
              v-for="name in builtinVariables"
              :key="`builtin-${name}`"
              :bordered="false"
              class="variable-tag"
              @click="appendVariable(name)"
            >{{ name }}</ATag>
          </div>
        </div>
      </div>
      <div class="config-row">
        <span class="config-row-label">
          计算表达式
          <ATooltip title="Groovy 表达式，最后一行的结果即为节点输出。输入绑定的参数名可直接作为变量使用，如 output[0].name">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
      </div>
      <ConfigCodeEditor
        :model-value="String(node.data.config?.expression || '')"
        language="txt"
        height="180px"
        placeholder="如：output[0].name"
        :maximize-target="panelRoot"
        @update:model-value="(v: any) => updateConfig('expression', v)"
        @maximize-change="onEditorMaximizeChange"
      />
      <AAlert
        class="usage-alert"
        type="info"
        message="用法示例"
        description="上游节点输出为 [{id:134, name:&quot;hanj&quot;}]，在输入绑定中将其绑定为参数 output，表达式 output[0].name 的计算结果即为 hanj。"
      />
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.constant-panel {
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

  &:first-child {
    margin-top: 0;
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

.variable-groups {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.variable-group {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.variable-group-label {
  flex-shrink: 0;
  width: 56px;
  font-size: 12px;
  line-height: 24px;
  color: rgba(0, 0, 0, 0.45);
}

.variable-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.variable-tag {
  margin: 0;
  cursor: pointer;
  user-select: none;
}

.usage-alert {
  margin-top: 12px;

  :deep(.ant-alert-description) {
    font-size: 12px;
  }
}
</style>

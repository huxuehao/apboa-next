<script setup lang="ts">
import { computed } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import WorkflowCompareToEditor from '@/components/workflow/fields/WorkflowCompareToEditor.vue'
import WorkflowArrayEditors from '@/components/workflow/fields/WorkflowArrayEditors.vue'
import SmartCodeEditor from '@/components/editor/SmartCodeEditor.vue'
import type { WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

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

const symbolOptions = [
  { label: '等于', value: 'EQ' },
  { label: '不等于', value: 'NE' },
  { label: '大于', value: 'GT' },
  { label: '小于', value: 'LT' },
  { label: '大于等于', value: 'GE' },
  { label: '小于等于', value: 'LE' },
  { label: '包含', value: 'CONTAINS' },
  { label: '不包含', value: 'NOT_CONTAINS' },
  { label: '全部是', value: 'IS_ALL' },
  { label: '开头匹配', value: 'STARTS_WITH' },
  { label: '结尾匹配', value: 'ENDS_WITH' },
  { label: '严格等于', value: 'EQUALS' },
  { label: '严格不等于', value: 'NOT_EQUALS' },
  { label: '为 true', value: 'IS_TRUE' },
  { label: '为 false', value: 'IS_FALSE' },
  { label: '表达式', value: 'EXPRESSION' },
]
const variableTypeOptions = [
  'String',
  'Long',
  'Integer',
  'Float',
  'Double',
  'Boolean',
  'Array',
  'Object',
].map((v: any) => ({ label: v, value: v }))
const isExpression = computed(() => (props.node.data.config?.symbol as string) === 'EXPRESSION')
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
      :current-node-id="node.id"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <AFormItem label="表达式引擎">
        <ASelect
          :value="node.data.config?.evaluatorType || 'GROOVY'"
          :options="[{ label: 'Groovy', value: 'GROOVY' }]"
          @update:value="(v: any) => updateConfig('evaluatorType', v)"
        />
      </AFormItem>
      <AFormItem label="计算对象">
        <ASegmented
          :value="node.data.config?.scope || 'SELF'"
          :options="[
            { label: '值本身', value: 'SELF' },
            { label: '长度', value: 'LENGTH' },
          ]"
          @update:value="(v: any) => updateConfig('scope', v)"
        />
      </AFormItem>
      <AFormItem label="运算符" required>
        <ASelect
          show-search
          :value="node.data.config?.symbol || 'EQ'"
          :options="symbolOptions"
          @update:value="(v: any) => updateConfig('symbol', v)"
        />
      </AFormItem>
      <AFormItem v-if="!isExpression" label="比较值">
        <WorkflowCompareToEditor
          :model-value="node.data.config?.compareTo"
          :nodes="nodes"
          :current-node-id="node.id"
          @update:model-value="(v: any) => updateConfig('compareTo', v)"
        />
      </AFormItem>
      <AFormItem v-if="isExpression" label="条件表达式">
        <SmartCodeEditor
          :model-value="String(node.data.config?.conditionExpression || '')"
          language="txt"
          theme="light"
          height="160px"
          :show-change-language="false"
          :show-theme-toggle="false"
          :show-fullscreen="true"
          placeholder="Groovy 表达式"
          @update:model-value="(v: any) => updateConfig('conditionExpression', v)"
        />
      </AFormItem>
      <AFormItem label="允许输入类型">
        <WorkflowArrayEditors
          :model-value="node.data.config?.allowInputType"
          type="stringList"
          :options="variableTypeOptions"
          @update:model-value="(v: any) => updateConfig('allowInputType', v)"
        />
      </AFormItem>
      <AFormItem label="输入为空时通过">
        <ASwitch
          :checked="Boolean(node.data.config?.inputIsNullUse)"
          @update:checked="(v: any) => updateConfig('inputIsNullUse', v)"
        />
      </AFormItem>
      <AFormItem label="True 分支节点ID">
        <AInput
          :value="String(node.data.config?.trueNextNodeId || '')"
          placeholder="满足条件后执行的节点"
          @update:value="(v: any) => updateConfig('trueNextNodeId', v)"
        />
      </AFormItem>
      <AFormItem label="False 分支节点ID">
        <AInput
          :value="String(node.data.config?.falseNextNodeId || '')"
          placeholder="不满足条件后执行的节点"
          @update:value="(v: any) => updateConfig('falseNextNodeId', v)"
        />
      </AFormItem>
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import SmartCodeEditor from '@/components/editor/SmartCodeEditor.vue'
import type { InputSourceType, WorkflowFlowNode, WorkflowInputConfig } from '@/types/workflow'

const props = defineProps<{
  modelValue?: WorkflowInputConfig[]
  nodes: WorkflowFlowNode[]
  currentNodeId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: WorkflowInputConfig[]]
}>()

const bindings = computed(() => (props.modelValue?.length ? props.modelValue : [{ name: 'input', sourceType: 'NODE_OUTPUT' as const }]))

const nodeOptions = computed(() =>
  props.nodes
    .filter((node) => node.id !== props.currentNodeId)
    .map((node) => ({
      label: `${node.data.label} · ${node.data.schema?.title || node.data.type}`,
      value: node.id,
      node,
    })),
)

function update(index: number, patch: Partial<WorkflowInputConfig>) {
  const next = bindings.value.map((item) => ({ ...item }))
  next[index] = { name: next[index]?.name || 'input', sourceType: next[index]?.sourceType || 'NODE_OUTPUT', ...next[index], ...patch }
  emit('update:modelValue', next)
}

function addBinding() {
  emit('update:modelValue', [...bindings.value, { name: `input${bindings.value.length + 1}`, sourceType: 'NODE_OUTPUT' }])
}

function removeBinding(index: number) {
  emit('update:modelValue', bindings.value.filter((_, itemIndex) => itemIndex !== index))
}
</script>

<template>
  <div class="binding-editor">
    <div v-for="(binding, index) in bindings" :key="index" class="binding-card">
      <div class="binding-head">
        <AInput
          :value="binding.name"
          placeholder="输入名"
          @update:value="(value: string) => update(index, { name: value })"
        />
        <AButton v-if="bindings.length > 1" danger type="text" @click="removeBinding(index)">删除</AButton>
      </div>

      <ASegmented
        :value="binding.sourceType"
        :options="[
          { label: '常量', value: 'CONSTANT' },
          { label: '变量', value: 'VARIABLE' },
          { label: '节点输出', value: 'NODE_OUTPUT' },
          { label: '表达式', value: 'EXPRESSION' },
        ]"
        @update:value="(value: InputSourceType) => update(index, { sourceType: value })"
      />

      <ATextarea
        v-if="binding.sourceType === 'CONSTANT'"
        :value="typeof binding.value === 'string' ? binding.value : JSON.stringify(binding.value ?? '', null, 2)"
        :auto-size="{ minRows: 2, maxRows: 6 }"
        placeholder="常量值，可填写字符串或 JSON"
        @update:value="(value: string) => update(index, { value })"
      />

      <AInput
        v-else-if="binding.sourceType === 'VARIABLE'"
        :value="binding.variableName"
        placeholder="全局变量名"
        @update:value="(value: string) => update(index, { variableName: value })"
      />

      <div v-else-if="binding.sourceType === 'NODE_OUTPUT'" class="node-output-grid">
        <ASelect
          show-search
          :value="binding.nodeId"
          :options="nodeOptions"
          placeholder="选择任意前置或已存在节点"
          @update:value="(value: string) => update(index, { nodeId: value })"
        />
        <AInput
          :value="binding.outputName || 'output'"
          placeholder="输出名"
          @update:value="(value: string) => update(index, { outputName: value })"
        />
        <div class="binding-tip">可选择任意节点输出；连线与循环依赖由保存校验兜底。</div>
      </div>

      <SmartCodeEditor
        v-else
        :model-value="binding.expression || ''"
        language="txt"
        theme="light"
        height="160px"
        :show-change-language="false"
        :show-theme-toggle="false"
        :show-fullscreen="false"
        placeholder="表达式"
        @update:model-value="(value: string) => update(index, { expression: value })"
      />
    </div>

    <AButton block size="small" class="add-binding" @click="addBinding">添加输入绑定</AButton>
  </div>
</template>

<style scoped lang="scss">
.binding-editor {
  display: grid;
  gap: 10px;
}

.binding-card {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.binding-head,
.node-output-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.node-output-grid {
  grid-template-columns: minmax(0, 1fr) 110px;
}

.binding-tip {
  grid-column: 1 / -1;
  color: #8c8c8c;
  font-size: 12px;
}

.add-binding {
  border-style: dashed;
}
</style>

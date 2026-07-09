<script setup lang="ts">
import { computed, inject } from 'vue'
import type { ComputedRef } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import BlurInput from '@/components/workflow/panels/shared/BlurInput.vue'
import NodeOutputSelector from './NodeOutputSelector.vue'
import VariableSelector from './VariableSelector.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowInputConfig } from '@/types/workflow'

const sourceTypeOptions = [
  { label: '常量', value: 'CONSTANT' as const, description: '直接填写固定值，支持字符串或 JSON 格式' },
  { label: '变量', value: 'VARIABLE' as const, description: '引用工作流全局变量，运行时动态注入' },
  { label: '节点输出', value: 'NODE_OUTPUT' as const, description: '引用其他节点的输出结果，构建节点间数据流' },
  { label: '表达式', value: 'EXPRESSION' as const, description: '使用 GroovyShell 表达式动态计算值' },
]

const props = withDefaults(defineProps<{
  modelValue?: WorkflowInputConfig[]
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  currentNodeId?: string
  maxBindings?: number
  readonlyName?: boolean
}>(), {
  readonlyName: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: WorkflowInputConfig[]]
}>()

const bindings = computed(() => (props.modelValue?.length ? props.modelValue : [{ name: 'input', sourceType: 'NODE_OUTPUT' as const }]))

const injectedEdges = inject<ComputedRef<WorkflowFlowEdge[]>>('workflowEdges', computed(() => []))
const parentUpstream = inject<ComputedRef<WorkflowFlowNode[]>>('parentUpstreamNodes', computed(() => []))

// 从 currentNodeId 出发沿边反向 BFS，收集所有上游节点
const upstreamNodes = computed(() => {
  const edges = injectedEdges.value
  if (!edges || !props.currentNodeId) {
    return []
  }

  // 构建反向邻接表：target → 所有 source（谁指向我）
  const reverseAdj = new Map<string, string[]>()
  for (const edge of edges) {
    const list = reverseAdj.get(edge.target)
    if (list) {
      list.push(edge.source)
    } else {
      reverseAdj.set(edge.target, [edge.source])
    }
  }

  // BFS：从当前节点出发，沿反向邻接表向上追溯
  const visited = new Set<string>()
  const queue: string[] = [props.currentNodeId]

  while (queue.length) {
    const nodeId = queue.shift()!
    if (visited.has(nodeId)) continue
    visited.add(nodeId)
    const sources = reverseAdj.get(nodeId)
    if (sources) {
      for (const source of sources) {
        if (!visited.has(source)) queue.push(source)
      }
    }
  }

  // 排除自身
  visited.delete(props.currentNodeId)

  // 从 nodes 中匹配
  const result: WorkflowFlowNode[] = []
  for (const nodeId of visited) {
    const node = props.nodes.find((n) => n.id === nodeId)
    if (node) result.push(node)
  }

  // 合并主流程上游节点（子流程模式下由 WorkflowEditorView 提供）
  const parentNodes = parentUpstream.value
  if (parentNodes.length) {
    const existingIds = new Set(result.map((n) => n.id))
    for (const pn of parentNodes) {
      if (!existingIds.has(pn.id)) {
        result.push({ ...pn, data: { ...pn.data, _parentSource: true } as any })
      }
    }
  }

  return result
})

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
        <BlurInput
          v-if="!readonlyName"
          :model-value="binding.name"
          placeholder="输入名"
          @update:model-value="(value: string) => update(index, { name: value })"
        />
        <span v-else class="binding-name-readonly">{{ binding.name }}</span>
        <AButton v-if="bindings.length > 1" danger type="text" @click="removeBinding(index)" style="margin-left: 5px; background-color: #FFF2F0;">删除</AButton>
      </div>

      <div class="source-type-segmented">
        <ATooltip
          v-for="opt in sourceTypeOptions"
          :key="opt.value"
          :title="opt.description"
          placement="topLeft"
          :overlay-inner-style="{ maxWidth: '170px' }"
        >
          <button
            :class="['segmented-option', { active: binding.sourceType === opt.value }]"
            type="button"
            @click="update(index, { sourceType: opt.value })"
          >
            <span class="segmented-label">{{ opt.label }}</span>
            <QuestionCircleOutlined class="segmented-help" />
          </button>
        </ATooltip>
      </div>

      <ATextarea
        v-if="binding.sourceType === 'CONSTANT'"
        :rows="1"
        :value="typeof binding.value === 'string' ? binding.value : JSON.stringify(binding.value ?? '', null, 2)"
        placeholder="常量值，可填写字符串或 JSON"
        @update:value="(value: string) => update(index, { value })"
      />

      <VariableSelector
        v-else-if="binding.sourceType === 'VARIABLE'"
        :model-value="binding.variableName"
        @update:model-value="(value: string) => update(index, { variableName: value })"
        @clear="update(index, { variableName: undefined })"
      />

      <NodeOutputSelector
        v-else-if="binding.sourceType === 'NODE_OUTPUT'"
        :upstream-nodes="upstreamNodes"
        :node-id="binding.nodeId"
        :output-name="binding.outputName"
        @select="({ nodeId, outputName }) => update(index, { nodeId, outputName })"
        @clear="update(index, { nodeId: undefined, outputName: undefined })"
      />

      <ATextarea
        v-else
        :rows="1"
        :value="typeof binding.value === 'string' ? binding.value : JSON.stringify(binding.value ?? '', null, 2)"
        placeholder="支持编写GroovyShell表单式"
        @update:value="(value: string) => update(index, { value })"
      />
    </div>

    <AButton v-if="!props.maxBindings || bindings.length < props.maxBindings" block size="small" class="add-binding" @click="addBinding">添加输入绑定</AButton>
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

.binding-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  //gap: 8px;
  align-items: center;
}

.source-type-segmented {
  display: flex;
  border-radius: 6px;
  background: #F2F4F7;
  padding: 2px;
}

.segmented-option {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 4px 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
  transition: all 0.2s;
  line-height: 1.4;

  &:hover {
    color: rgba(0, 0, 0, 0.85);
  }

  &.active {
    background: #fff;
    color: rgba(0, 0, 0, 0.88);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  }
}

.segmented-help {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.25);
  transition: color 0.2s;

  .segmented-option:hover & {
    color: rgba(0, 0, 0, 0.45);
  }
}

.add-binding {
  border-style: dashed;
}

.binding-name-readonly {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 11px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  background: #F2F4F7;
  border-radius: 6px;
}
</style>

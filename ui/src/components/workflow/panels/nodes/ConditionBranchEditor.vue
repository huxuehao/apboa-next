<script setup lang="ts">
import { computed, ref } from 'vue'
import BlurInput from '../shared/BlurInput.vue'
import NodeOutputSelector from '@/components/workflow/bindings/NodeOutputSelector.vue'
import NextNodeSelector from '@/components/workflow/bindings/NextNodeSelector.vue'
import ConditionSymbolGuideModal from '../shared/ConditionSymbolGuideModal.vue'
import { CaretRightOutlined } from '@ant-design/icons-vue'
import type { WorkflowFlowNode, WorkflowFlowEdge } from '@/types/workflow'

export interface ConditionBranch {
  scope?: string
  inputIsNullUse?: boolean
  symbol?: string
  conditionExpression?: string
  compareTo?: Record<string, unknown>
  nextNodeId?: string
}

const props = defineProps<{
  branch: ConditionBranch
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  currentNodeId: string
  upstreamNodes: WorkflowFlowNode[]
  inputName?: string
}>()
const emit = defineEmits<{ 'update:branch': [branch: ConditionBranch] }>()

const showAdvanced = ref(false)

function patchBranch(patch: Partial<ConditionBranch>) {
  emit('update:branch', { ...props.branch, ...patch })
}

const allSymbolOptions = [
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

// LENGTH 模式下需隐藏的运算符
const lengthHiddenSymbols = new Set([
  'CONTAINS', 'NOT_CONTAINS', 'IS_ALL',
  'STARTS_WITH', 'ENDS_WITH',
  'EQUALS', 'NOT_EQUALS',
  'IS_TRUE', 'IS_FALSE',
])

const scope = computed(() => props.branch.scope || 'SELF')

const symbolOptions = computed(() => {
  if (scope.value === 'LENGTH') {
    return allSymbolOptions.filter((opt) => !lengthHiddenSymbols.has(opt.value))
  }
  return allSymbolOptions
})

const isExpression = computed(() => props.branch.symbol === 'EXPRESSION')

// ---- 比较值相关 ----
const compareToValue = computed<Record<string, unknown>>(() => {
  const raw = props.branch.compareTo
  if (raw && typeof raw === 'object') return raw
  return { type: 'CONSTANT', value: '' }
})

const compareSourceNodeId = computed(() => compareToValue.value.sourceNodeId as string | undefined)
const compareOutputName = computed(() => compareToValue.value.value as string | undefined)

function updateCompareTo(key: string, nextValue: unknown) {
  patchBranch({ compareTo: { ...compareToValue.value, [key]: nextValue } })
}

function onCompareSelect(payload: { nodeId: string; outputName: string }) {
  patchBranch({ compareTo: { ...compareToValue.value, sourceNodeId: payload.nodeId, value: payload.outputName } })
}

function onCompareClear() {
  patchBranch({ compareTo: { ...compareToValue.value, sourceNodeId: undefined, value: undefined } })
}
</script>

<template>
  <div class="branch-editor">
    <div class="config-row">
      <span class="config-row-label">运算符 <ConditionSymbolGuideModal /></span>
      <ASelect
        show-search
        :value="branch.symbol || 'EQ'"
        :options="symbolOptions"
        style="width: 140px"
        @update:value="(v: any) => patchBranch({ symbol: v })"
      />
    </div>
    <div v-if="isExpression" class="config-row expression-row">
      <span class="config-row-label" style="margin-right: 10px">条件表达式</span>
      <ATextarea
        :rows="1"
        :value="String(branch.conditionExpression || '')"
        :placeholder="`Groovy 表达式，变量名 ${inputName || '未知'}`"
        class="expression-input"
        @update:value="(v: any) => patchBranch({ conditionExpression: v })"
      />
    </div>
    <div v-else class="config-row compare-full-row">
      <span class="config-row-label">比较值</span>
      <div class="compare-type">
        <ASelect
          :value="compareToValue.type || 'CONSTANT'"
          :options="[
            { label: '常量', value: 'CONSTANT' },
            { label: '节点输出', value: 'VARIABLE' },
          ]"
          style="width: 100px"
          @update:value="(v: any) => updateCompareTo('type', v)"
        />
      </div>
      <div class="compare-control">
        <NodeOutputSelector
          v-if="compareToValue.type === 'VARIABLE'"
          :upstream-nodes="upstreamNodes"
          :node-id="compareSourceNodeId"
          :output-name="compareOutputName"
          @select="onCompareSelect"
          @clear="onCompareClear"
        />
        <BlurInput
          v-else
          :model-value="String(compareToValue.value ?? '')"
          placeholder="请输入比较值"
          @update:model-value="(next: string) => updateCompareTo('value', next)"
        />
      </div>
    </div>
    <div class="advanced-toggle" @click="showAdvanced = !showAdvanced" v-if="!isExpression">
      <CaretRightOutlined :class="{ rotated: showAdvanced }" class="toggle-icon" />
      <span>高级选项</span>
    </div>
    <div v-show="showAdvanced" class="advanced-options" v-if="!isExpression">
      <div class="config-row">
        <span class="config-row-label">计算对象</span>
        <ASegmented
          :value="branch.scope || 'SELF'"
          :options="[
            { label: '值本身', value: 'SELF' },
            { label: '长度', value: 'LENGTH' },
          ]"
          @update:value="(v: any) => patchBranch({ scope: String(v) })"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">输入为空时视为True</span>
        <ASwitch
          :checked="Boolean(branch.inputIsNullUse)"
          @update:checked="(v: any) => patchBranch({ inputIsNullUse: Boolean(v) })"
        />
      </div>
    </div>
    <div class="branch-row">
      <span class="branch-hint">跳转到</span>
      <div class="next-node-selector">
        <NextNodeSelector
          :nodes="nodes"
          :edges="edges"
          :current-node-id="currentNodeId"
          :selected-node-id="branch.nextNodeId || undefined"
          @select="(id: string) => patchBranch({ nextNodeId: id })"
          @clear="patchBranch({ nextNodeId: undefined })"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.config-row-label {
  flex-shrink: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
}

.compare-control, .next-node-selector {
  flex: 1;
  min-width: 0;
}

// ── 高级选项折叠 ──
.advanced-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding: 4px 0;
  color: #8c8c8c;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;

  &:hover {
    color: #1677ff;
  }
}

.toggle-icon {
  font-size: 10px;
  transition: transform 0.2s ease;
}

.toggle-icon.rotated {
  transform: rotate(90deg);
}

.advanced-options {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #f0f0f0;
}

// ── 比较值行 ──
.compare-full-row {
  .config-row-label {
    margin-right: 20px;
  }
}

.compare-type {
  margin-right: 6px;
}

.expression-row {
  align-items: flex-start;

  .config-row-label {
    margin-top: 4px;
  }
}

.expression-input {
  flex: 1;
  min-width: 0;
}

// ── 跳转分支行 ──
.branch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #f0f0f0;
}

.branch-hint {
  flex-shrink: 0;
  font-size: 13px;
  color: #8c8c8c;
}
</style>

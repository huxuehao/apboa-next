<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import type { ComputedRef } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import NodeOutputSelector from '@/components/workflow/bindings/NodeOutputSelector.vue'
import NextNodeSelector from '@/components/workflow/bindings/NextNodeSelector.vue'
import { CaretRightOutlined } from '@ant-design/icons-vue'
import type { WorkflowFlowNode, WorkflowResourceMaps, WorkflowFlowEdge } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const injectedEdges = inject<ComputedRef<WorkflowFlowEdge[]>>('workflowEdges', computed(() => []))

const showAdvanced = ref(false)

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
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

const scope = computed(() => (props.node.data.config?.scope as string) || 'SELF')

const symbolOptions = computed(() => {
  if (scope.value === 'LENGTH') {
    return allSymbolOptions.filter((opt) => !lengthHiddenSymbols.has(opt.value))
  }
  return allSymbolOptions
})

const isExpression = computed(() => (props.node.data.config?.symbol as string) === 'EXPRESSION')

// ---- 比较值相关 ----
const compareToValue = computed<Record<string, unknown>>(() => {
  const raw = props.node.data.config?.compareTo
  if (raw && typeof raw === 'object') return raw as Record<string, unknown>
  return { type: 'CONSTANT', value: '' }
})

const compareSourceNodeId = computed(() => compareToValue.value.sourceNodeId as string | undefined)
const compareOutputName = computed(() => compareToValue.value.value as string | undefined)

function updateCompareTo(key: string, nextValue: unknown) {
  updateConfig('compareTo', { ...compareToValue.value, [key]: nextValue })
}

function onCompareSelect(payload: { nodeId: string; outputName: string }) {
  updateConfig('compareTo', { ...compareToValue.value, sourceNodeId: payload.nodeId, value: payload.outputName })
}

function onCompareClear() {
  updateConfig('compareTo', { ...compareToValue.value, sourceNodeId: undefined, value: undefined })
}

// 计算上游节点（与 InputBindingEditor 相同逻辑）
const upstreamNodes = computed(() => {
  const edges = injectedEdges.value
  if (!edges || !props.node.id) return []

  const reverseAdj = new Map<string, string[]>()
  for (const edge of edges) {
    const list = reverseAdj.get(edge.target)
    if (list) {
      list.push(edge.source)
    } else {
      reverseAdj.set(edge.target, [edge.source])
    }
  }

  const visited = new Set<string>()
  const queue: string[] = [props.node.id]

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

  visited.delete(props.node.id)

  const result: WorkflowFlowNode[] = []
  for (const nodeId of visited) {
    const node = props.nodes.find((n) => n.id === nodeId)
    if (node) result.push(node)
  }
  return result
})
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
      :max-bindings="1"
      :readonly-name="true"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <!-- IF · 条件判断 -->
      <div class="logic-block if-block">
        <div class="block-header">
          <span class="block-tag if-tag">IF</span>
          <span class="block-label">条件判断</span>
        </div>
        <div class="block-body">
          <div class="config-row">
            <span class="config-row-label">关系运算符</span>
            <ASelect
              show-search
              :value="node.data.config?.symbol || 'EQ'"
              :options="symbolOptions"
              style="width: 140px"
              @update:value="(v: any) => updateConfig('symbol', v)"
            />
          </div>
          <div v-if="isExpression" class="config-row expression-row">
            <span class="config-row-label" style="margin-right: 10px">条件表达式</span>
            <ATextarea
              :rows="1"
              :model-value="String(node.data.config?.conditionExpression || '')"
              :placeholder="`Groovy 表达式，变量名 ${node.data.inputConfigs?.[0]?.name || '未知'}`"
              class="expression-input"
              @update:model-value="(v: any) => updateConfig('conditionExpression', v)"
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
                :value="node.data.config?.scope || 'SELF'"
                :options="[
                  { label: '值本身', value: 'SELF' },
                  { label: '长度', value: 'LENGTH' },
                ]"
                @update:value="(v: any) => updateConfig('scope', v)"
              />
            </div>
            <div class="config-row">
              <span class="config-row-label">输入为空时视为True</span>
              <ASwitch
                :checked="Boolean(node.data.config?.inputIsNullUse)"
                @update:checked="(v: any) => updateConfig('inputIsNullUse', v)"
              />
            </div>
          </div>
        </div>
      </div>
      <!-- THEN · 条件成立 -->
      <div class="logic-block then-block">
        <div class="block-header">
          <span class="block-tag then-tag">THEN</span>
          <span class="block-label">条件成立</span>
        </div>
        <div class="block-body">
          <div class="branch-row">
            <span class="branch-hint">跳转到</span>
            <div class="next-node-selector">
              <NextNodeSelector
                :nodes="nodes"
                :edges="injectedEdges"
                :current-node-id="node.id"
                :selected-node-id="(node.data.config?.trueNextNodeId as string) || undefined"
                @select="(id: string) => updateConfig('trueNextNodeId', id)"
                @clear="updateConfig('trueNextNodeId', undefined)"
              />
            </div>
          </div>
        </div>
      </div>
      <!-- ELSE · 条件不成立 -->
      <div class="logic-block else-block">
        <div class="block-header">
          <span class="block-tag else-tag">ELSE</span>
          <span class="block-label">条件不成立</span>
        </div>
        <div class="block-body">
          <div class="branch-row">
            <span class="branch-hint">跳转到</span>
            <div class="next-node-selector">
              <NextNodeSelector
                :nodes="nodes"
                :edges="injectedEdges"
                :current-node-id="node.id"
                :selected-node-id="(node.data.config?.falseNextNodeId as string) || undefined"
                @select="(id: string) => updateConfig('falseNextNodeId', id)"
                @clear="updateConfig('falseNextNodeId', undefined)"
              />
            </div>
          </div>
        </div>
      </div>
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
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

.config-row-required {
  color: #ff4d4f;
  margin-right: 4px;
  font-family: SimSun, sans-serif;
}

.compare-control, .next-node-selector {
  flex: 1;
  min-width: 0;
}

// ── IF / THEN / ELSE 三段式区块 ──
.logic-block {
  border: 1px solid #ececec;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;

  &:last-child {
    margin-bottom: 0;
  }
}

.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 14px;
  background: #F2F4F7;
}

.block-tag {
  display: inline-flex;
  align-items: center;
  min-width: 46px;
  height: 22px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.3px;
  // color: #fff;
  line-height: 1;
}

.block-label {
  font-size: 13px;
  font-weight: 500;
  color: #999;
}

.block-body {
  padding: 10px 14px 14px;
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

// ── THEN / ELSE 分支行 ──
.branch-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.branch-hint {
  flex-shrink: 0;
  font-size: 13px;
  color: #8c8c8c;
}
</style>

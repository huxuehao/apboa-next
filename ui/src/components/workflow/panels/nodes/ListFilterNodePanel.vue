<script setup lang="ts">
import { ref, computed } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import NodeOutputSelector from '@/components/workflow/bindings/NodeOutputSelector.vue'
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
function updateCompareTo(key: string, value: unknown) {
  updateConfig('compareTo', { ...(props.node.data.config?.compareTo || {}), type: 'CONSTANT', value: '', [key]: value })
}
function onCompareSelect(payload: { nodeId: string; outputName: string }) {
  updateConfig('compareTo', {
    ...(props.node.data.config?.compareTo || {}),
    type: 'VARIABLE',
    sourceNodeId: payload.nodeId,
    value: payload.outputName,
  })
}

const isSimple = computed(() => (props.node.data.config?.mode as string) !== 'EXPRESSION')

const upstreamNodes = computed(() => {
  if (!props.edges.length || !props.node.id) return []

  const reverseAdj = new Map<string, string[]>()
  for (const edge of props.edges) {
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
  return props.nodes.filter((n) => visited.has(n.id))
})

const symbolToType: Record<string, string> = {
  EQ: 'NUMBER', NE: 'NUMBER', GT: 'NUMBER', LT: 'NUMBER', GE: 'NUMBER', LE: 'NUMBER',
  CONTAINS: 'STRING', NOT_CONTAINS: 'STRING', STARTS_WITH: 'STRING', ENDS_WITH: 'STRING', EQUALS: 'STRING', NOT_EQUALS: 'STRING',
  IS_TRUE: 'BOOLEAN', IS_FALSE: 'BOOLEAN',
}

function updateSimpleSymbol(v: string) {
  const type = symbolToType[v]
  const patch: Record<string, unknown> = { simpleSymbol: v }
  if (type) patch.supportType = type
  updateNode({ config: { ...(props.node.data.config || {}), ...patch } })
}

const simpleSymbolOptions = [
  { label: '等于 (==)', value: 'EQ' },
  { label: '不等于 (!=)', value: 'NE' },
  { label: '大于 (>)', value: 'GT' },
  { label: '小于 (<)', value: 'LT' },
  { label: '大于等于 (>=)', value: 'GE' },
  { label: '小于等于 (<=)', value: 'LE' },
  { label: '包含', value: 'CONTAINS' },
  { label: '不包含', value: 'NOT_CONTAINS' },
  { label: '开头是', value: 'STARTS_WITH' },
  { label: '结尾是', value: 'ENDS_WITH' },
  { label: '等于', value: 'EQUALS' },
  { label: '不等于', value: 'NOT_EQUALS' },
  { label: '为 true', value: 'IS_TRUE' },
  { label: '为 false', value: 'IS_FALSE' },
]



const isVariableCompare = computed(
  () => (props.node.data.config?.compareTo as Record<string, unknown>)?.type === 'VARIABLE',
)
</script>

<template>
  <div ref="panelRoot" class="filter-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
        <span class="config-row-label">过滤模式</span>
        <ASegmented
          :value="node.data.config?.mode || 'SIMPLE'"
          :options="[
            { label: '简单', value: 'SIMPLE' },
            { label: '表达式', value: 'EXPRESSION' },
          ]"
          @update:value="(v: any) => updateConfig('mode', v)"
        />
      </div>
      <template v-if="isSimple">
        <div class="condition-card">
          <div class="condition-header">条件</div>
          <div class="condition-body">
            <div class="condition-field">
              <span class="condition-hint">当</span>
              <BlurInput
                :model-value="String(node.data.config?.condition || '')"
                placeholder="item 或 item.属性"
                style="flex: 1"
                @update:model-value="(v: any) => updateConfig('condition', v)"
              />
            </div>
            <div class="condition-operator">
              <span class="condition-hint">满足</span>
              <ASelect
                show-search
                :value="node.data.config?.simpleSymbol"
                :options="simpleSymbolOptions"
                style="width: 100%"
                placeholder="请选择"
                @change="(v: any) => updateSimpleSymbol(v)"
              />
              <ASegmented
                :value="(node.data.config?.compareTo as Record<string, unknown>)?.type || 'CONSTANT'"
                :options="[
                  { label: '常量', value: 'CONSTANT' },
                  { label: '节点输出', value: 'VARIABLE' },
                ]"
                @update:value="(v: any) => updateCompareTo('type', v)"
              />
            </div>
            <div class="compare-area">
              <NodeOutputSelector
                v-if="isVariableCompare"
                :upstream-nodes="upstreamNodes"
                :node-id="(node.data.config?.compareTo as Record<string, unknown>)?.sourceNodeId as string || undefined"
                :output-name="(node.data.config?.compareTo as Record<string, unknown>)?.value as string || undefined"
                @select="onCompareSelect"
                @clear="updateCompareTo('sourceNodeId', undefined)"
              />
              <BlurInput
                v-else
                :model-value="String((node.data.config?.compareTo as Record<string, unknown>)?.value ?? '')"
                placeholder="输入比较值"
                @update:model-value="(v: any) => updateCompareTo('value', v)"
              />
              <div class="condition-value">
                <span class="condition-hint">时保留</span>
              </div>
            </div>
          </div>
          <div class="condition-null">
            <span class="null-label">元素为空时保留</span>
            <ASwitch
              :checked="Boolean(node.data.config?.itemIsNullUse)"
              @update:checked="(v: any) => updateConfig('itemIsNullUse', v)"
            />
          </div>
        </div>
      </template>
      <template v-else>
        <div class="config-row">
          <span class="config-row-label">表达式引擎</span>
          <ASelect
            :value="node.data.config?.evaluatorType || 'GROOVY'"
            :options="[{ label: 'Groovy', value: 'GROOVY' }]"
            style="width: 160px"
            @update:value="(v: any) => updateConfig('evaluatorType', v)"
          />
        </div>
        <ConfigCodeEditor
          :model-value="String(node.data.config?.condition || '')"
          language="txt"
          placeholder="变量名仅可为 item，如 item.age > 18"
          :maximize-target="panelRoot"
          @update:model-value="(v: any) => updateConfig('condition', v)"
          @maximize-change="onEditorMaximizeChange"
        />
      </template>
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.filter-panel {
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

.compare-row {
  align-items: flex-start;
}

// ── 条件卡片 ──
.condition-card {
  border: 1px solid #ececec;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
}

.condition-header {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.65);
  background: #fafafa;
  border-bottom: 1px solid #ececec;
}

.condition-body {
  padding: 12px;
}

.condition-field,
.condition-operator {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.condition-value {
  margin-bottom: 8px;
}

.condition-hint {
  flex-shrink: 0;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
  width: 32px;
  text-align: right;
}

// ── 比较值区域 ──
.compare-area {
  padding-left: 38px;
}

.compare-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.compare-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}

.compare-control {
  display: flex;
  align-items: center;
  gap: 6px;
}

// ── 空值处理 ──
.condition-null {
  display: flex;
  align-items: center;
  justify-content: end;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #ececec;
  background: #fafafa;
}

.null-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}
</style>

<script setup lang="ts">
import { computed, inject, nextTick, onMounted, watch } from 'vue'
import type { ComputedRef } from 'vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import NextNodeSelector from '@/components/workflow/bindings/NextNodeSelector.vue'
import ConditionBranchEditor, { type ConditionBranch } from './ConditionBranchEditor.vue'
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { WorkflowFlowNode, WorkflowResourceMaps, WorkflowFlowEdge } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const injectedEdges = inject<ComputedRef<WorkflowFlowEdge[]>>('workflowEdges', computed(() => []))

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

function defaultBranch(): ConditionBranch {
  return { scope: 'SELF', inputIsNullUse: false, symbol: 'EQ', compareTo: { type: 'CONSTANT', value: '' }, conditionExpression: '', nextNodeId: undefined }
}

const branches = computed<ConditionBranch[]>(() => {
  const raw = props.node.data.config?.branches
  return Array.isArray(raw) ? (raw as ConditionBranch[]) : []
})

const elseNextNodeId = computed(() => (props.node.data.config?.elseNextNodeId as string) || undefined)

// 旧版平铺配置规范化为多分支结构（与后端 Config.normalize 规则一致）
onMounted(() => {
  const config = props.node.data.config || {}
  if (branches.value.length > 0 || !config.symbol) {
    if (branches.value.length === 0) {
      updateConfig('branches', [defaultBranch()])
    }
    return
  }
  const migrated: ConditionBranch = {
    scope: (config.scope as string) || 'SELF',
    inputIsNullUse: Boolean(config.inputIsNullUse),
    symbol: config.symbol as string,
    conditionExpression: (config.conditionExpression as string) || '',
    compareTo: (config.compareTo as Record<string, unknown>) || { type: 'CONSTANT', value: '' },
    nextNodeId: (config.trueNextNodeId as string) || undefined,
  }
  updateNode({
    config: {
      ...config,
      branches: [migrated],
      elseNextNodeId: (config.falseNextNodeId as string) || undefined,
      scope: undefined,
      inputIsNullUse: undefined,
      symbol: undefined,
      conditionExpression: undefined,
      compareTo: undefined,
      trueNextNodeId: undefined,
      falseNextNodeId: undefined,
    },
  })
})

function updateBranch(index: number, branch: ConditionBranch) {
  const list = [...branches.value]
  list[index] = branch
  updateConfig('branches', list)
}

function addBranch() {
  updateConfig('branches', [...branches.value, defaultBranch()])
}

function removeBranch(index: number) {
  const list = [...branches.value]
  list.splice(index, 1)
  updateConfig('branches', list)
}

function moveBranch(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= branches.value.length) return
  const list = [...branches.value]
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item!)
  updateConfig('branches', list)
}

// 下游节点集合（用于清理断开连线后的残留路由）
const downstreamNodeIds = computed(() => {
  const ids = new Set<string>()
  for (const edge of injectedEdges.value || []) {
    if (edge.source === props.node.id) ids.add(edge.target)
  }
  return ids
})

// 自动清理：下游节点断开时清空对应分支的跳转目标（保留条件本身）
let branchSyncGuard = false
watch(downstreamNodeIds, () => {
  if (branchSyncGuard) return
  const ids = downstreamNodeIds.value
  let changed = false
  const cleaned = branches.value.map((branch) => {
    if (branch.nextNodeId && !ids.has(branch.nextNodeId)) {
      changed = true
      return { ...branch, nextNodeId: undefined }
    }
    return branch
  })
  const nextElse = elseNextNodeId.value && !ids.has(elseNextNodeId.value) ? undefined : elseNextNodeId.value
  if (!changed && nextElse === elseNextNodeId.value) return
  branchSyncGuard = true
  updateNode({ config: { ...(props.node.data.config || {}), branches: cleaned, elseNextNodeId: nextElse } })
  nextTick(() => { branchSyncGuard = false })
})

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
    <PanelSection title="条件分支">
      <!-- IF / ELSE IF 分支列表 -->
      <div
        v-for="(branch, index) in branches"
        :key="index"
        class="logic-block"
      >
        <div class="block-header">
          <div class="block-header-left">
            <span class="block-tag" :class="index === 0 ? 'if-tag' : 'elseif-tag'">
              {{ index === 0 ? 'IF' : 'ELSE IF' }}
            </span>
            <span class="block-label">条件判断</span>
          </div>
          <div class="block-actions">
            <AButton
              v-if="index > 0"
              type="text"
              size="small"
              :disabled="index === 1"
              @click="moveBranch(index, -1)"
            >
              <template #icon><ArrowUpOutlined /></template>
            </AButton>
            <AButton
              v-if="index > 0"
              type="text"
              size="small"
              :disabled="index === branches.length - 1"
              @click="moveBranch(index, 1)"
            >
              <template #icon><ArrowDownOutlined /></template>
            </AButton>
            <AButton
              v-if="index > 0"
              type="text"
              size="small"
              danger
              @click="removeBranch(index)"
            >
              <template #icon><DeleteOutlined /></template>
            </AButton>
          </div>
        </div>
        <div class="block-body">
          <ConditionBranchEditor
            :branch="branch"
            :nodes="nodes"
            :edges="injectedEdges"
            :current-node-id="node.id"
            :upstream-nodes="upstreamNodes"
            :input-name="node.data.inputConfigs?.[0]?.name"
            @update:branch="(next: any) => updateBranch(index, next)"
          />
        </div>
      </div>
      <!-- 添加 ELSE IF 分支 -->
      <AButton type="dashed" block class="add-branch-btn" @click="addBranch">
        <template #icon><PlusOutlined /></template>
        添加 ELSE IF 分支
      </AButton>
      <!-- ELSE · 兜底分支 -->
      <div class="logic-block else-block">
        <div class="block-header">
          <div class="block-header-left">
            <span class="block-tag else-tag">ELSE</span>
            <span class="block-label">所有分支均未命中</span>
          </div>
        </div>
        <div class="block-body">
          <div class="branch-row">
            <span class="branch-hint">跳转到</span>
            <div class="next-node-selector">
              <NextNodeSelector
                :nodes="nodes"
                :edges="injectedEdges"
                :current-node-id="node.id"
                :selected-node-id="elseNextNodeId"
                @select="(id: string) => updateConfig('elseNextNodeId', id)"
                @clear="updateConfig('elseNextNodeId', undefined)"
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
// ── IF / ELSE IF / ELSE 区块 ──
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
  padding: 6px 14px;
  background: #F2F4F7;
}

.block-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.block-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.3px;
  line-height: 1;
  white-space: nowrap;
}

.block-label {
  font-size: 13px;
  font-weight: 500;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.block-body {
  padding: 10px 14px 14px;
}

.add-branch-btn {
  margin-bottom: 12px;
  color: #8c8c8c;
}

// ── ELSE 分支行 ──
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

.next-node-selector {
  flex: 1;
  min-width: 0;
}
</style>

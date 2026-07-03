<script setup lang="ts">
import { computed, inject, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import type { ComputedRef } from 'vue'
import { HolderOutlined } from '@ant-design/icons-vue'
import Sortable from 'sortablejs'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowInputConfig, WorkflowOutputConfig } from '@/types/workflow'
import PanelSection from '@/components/workflow/panels/shared/PanelSection.vue'

const nodeIconMap: Record<string, IconName> = {
  START: 'nodestart',
  END: 'nodeend',
  IF_ELSE: 'nodeif_else',
  CACHE_FETCH: 'nodecache',
  CACHE_SET: 'nodecache',
  CACHE_REMOVE: 'nodecache',
  CACHE_REFRESH: 'nodecache',
  DB_SELECT: 'nodedb_select',
  DB_INSERT: 'nodedb_insert',
  DB_UPDATE: 'nodedb_update',
  DB_DELETE: 'nodedb_delete',
  MQ_PUSH: 'nodemq_push',
  HTTP_EXTERNAL: 'nodehttp_external',
  CODE: 'nodecode',
  ITERATE: 'nodeiterate',
  LOOP: 'nodeloop',
  LIST_FILTER: 'nodelist_filter',
  LIST_SORT: 'nodelist_sort',
  STRING_SPLIT: 'nodestring_split',
  STRING_TEMPLATE: 'nodestring_template',
  SERIALIZE: 'nodeserialize',
  UNSERIALIZE: 'nodeunserialize',
  VARIABLE_AGG: 'nodevariable_agg',
  NON_EMPTY_SELECT: 'nodenon_empty_select',
  MATCH_RESULT: 'nodematch_result',
}

function getIconName(type: string): IconName {
  return nodeIconMap[type] || 'nodecode'
}

function getNodeOutputs(node: WorkflowFlowNode): WorkflowOutputConfig[] {
  if (node.data.type === 'START') {
    const params = (node.data.config?.params as Array<{ name: string; type: string; description?: string }>) || []
    return params.map((p) => ({
      name: p.name,
      type: p.type || 'Object',
      description: p.description || '',
    }))
  }
  return node.data.outputConfigs || []
}

function formatNodeOutputLabel(node: WorkflowFlowNode): string {
  const outputs = getNodeOutputs(node)
  const output = outputs[0]
  if (!output) return node.data.label || node.id
  return `${node.data.label || node.id}  ›  ${output.name}`
}

const props = defineProps<{
  modelValue?: WorkflowInputConfig[]
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  currentNodeId: string
  draggable: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: WorkflowInputConfig[]]
}>()

const injectedEdges = inject<ComputedRef<WorkflowFlowEdge[]>>('workflowEdges', computed(() => []))

// 从 currentNodeId 出发沿边反向 BFS，收集所有直接上游节点（仅一跳）
const upstreamNodeIds = computed(() => {
  const edges = injectedEdges.value.length ? injectedEdges.value : props.edges
  if (!edges || !props.currentNodeId) return []

  const sources = new Set<string>()
  for (const edge of edges) {
    if (edge.target === props.currentNodeId) {
      sources.add(edge.source)
    }
  }
  return [...sources]
})

// 收集所有上游节点信息
const upstreamNodes = computed(() => {
  return upstreamNodeIds.value
    .map((id) => props.nodes.find((n) => n.id === id))
    .filter((n): n is WorkflowFlowNode => !!n)
})

// 当前绑定列表（用于显示），基于 modelValue 中的顺序
const bindings = computed(() => {
  if (!props.modelValue?.length) return []
  return props.modelValue.filter((cfg) => cfg.nodeId && upstreamNodeIds.value.includes(cfg.nodeId))
})

// 显示列表：将 bindings 与 upstreamNodes 交叉映射，确保显示顺序与绑定顺序一致
interface DisplayItem {
  nodeId: string
  label: string
  type: string
  color: string
}

const displayItems = computed<DisplayItem[]>(() => {
  const ordered: DisplayItem[] = []
  const seen = new Set<string>()

  for (const binding of bindings.value) {
    if (!binding.nodeId || seen.has(binding.nodeId)) continue
    const node = upstreamNodes.value.find((n) => n.id === binding.nodeId)
    if (node) {
      seen.add(binding.nodeId)
      ordered.push({
        nodeId: node.id,
        label: formatNodeOutputLabel(node),
        type: node.data.type,
        color: node.data.schema?.color || '#1677ff',
      })
    }
  }

  for (const node of upstreamNodes.value) {
    if (!seen.has(node.id)) {
      ordered.push({
        nodeId: node.id,
        label: formatNodeOutputLabel(node),
        type: node.data.type,
        color: node.data.schema?.color || '#1677ff',
      })
    }
  }

  return ordered
})

// 自动同步：当上游节点变化时，自动更新 modelValue
let syncing = false
watch(
  [upstreamNodeIds, () => props.modelValue],
  () => {
    if (syncing) return
    const edges = injectedEdges.value.length ? injectedEdges.value : props.edges
    if (!edges || !props.currentNodeId) return

    const currentIds = new Set(upstreamNodeIds.value)
    const existingBindings = props.modelValue || []

    // 移除已断开连接的节点
    const filtered = existingBindings.filter((cfg) => cfg.nodeId && currentIds.has(cfg.nodeId))

    // 添加新连接但尚未在绑定中的节点
    const existingIds = new Set(filtered.map((cfg) => cfg.nodeId))
    for (const nodeId of currentIds) {
      if (!existingIds.has(nodeId)) {
        filtered.push({ name: 'input', sourceType: 'NODE_OUTPUT', nodeId })
      }
    }

    // 检查是否需要更新
    const needUpdate =
      filtered.length !== existingBindings.length ||
      filtered.some((cfg, i) => cfg.nodeId !== existingBindings[i]?.nodeId)

    if (needUpdate) {
      syncing = true
      emit('update:modelValue', filtered)
      nextTick(() => { syncing = false })
    }
  },
  { immediate: true, deep: false },
)

// 拖拽排序
const listRef = ref<HTMLElement | null>(null)
let sortableInstance: ReturnType<typeof Sortable.create> | null = null

function setupSortable() {
  if (!listRef.value) return
  if (sortableInstance) sortableInstance.destroy()

  sortableInstance = Sortable.create(listRef.value, {
    animation: 150,
    handle: '.auto-binding-drag-handle',
    ghostClass: 'auto-binding-ghost',
    chosenClass: 'auto-binding-chosen',
    dragClass: 'auto-binding-drag',
    onStart: () => {
      document.body.classList.add('dragging')
    },
    onEnd: (evt: { oldIndex?: number; newIndex?: number }) => {
      document.body.classList.remove('dragging')
      const { oldIndex, newIndex } = evt
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return

      // 根据拖拽前后的索引重新排列 displayItems，再重建 modelValue
      const items = [...displayItems.value]
      const [moved] = items.splice(oldIndex, 1)
      if (moved) {
        items.splice(newIndex, 0, moved)
      }

      const newBindings: WorkflowInputConfig[] = items.map((item) => ({
        name: 'input',
        sourceType: 'NODE_OUTPUT',
        nodeId: item.nodeId,
      }))

      syncing = true
      emit('update:modelValue', newBindings)
      nextTick(() => { syncing = false })
    },
  })
}

onMounted(() => {
  nextTick(() => setupSortable())
})

watch(displayItems, () => {
  nextTick(() => {
    if (!sortableInstance && listRef.value) {
      setupSortable()
    }
  })
})

onUnmounted(() => {
  if (sortableInstance) {
    sortableInstance.destroy()
    sortableInstance = null
  }
})
</script>

<template>
  <PanelSection title="输入节点">
    <div
      ref="listRef"
      class="auto-binding-list"
      :class="{ empty: !displayItems.length }"
    >
      <div
        v-for="item in displayItems"
        :key="item.nodeId"
        class="auto-binding-item"
      >
        <span v-if="draggable" class="auto-binding-drag-handle">
          <HolderOutlined />
        </span>
        <div class="auto-binding-display selector-trigger">
          <span class="trigger-text">
            <span class="trigger-icon">
              <IconFont
                :name="getIconName(item.type)"
                :size="14"
                :color="item.color"
              />
              <span style="margin-left: 4px;">{{ item.label }}</span>
            </span>
          </span>
        </div>
      </div>
      <div v-if="!displayItems.length" class="auto-binding-empty">
        暂无连接的上游节点，请先连线
      </div>
    </div>
  </PanelSection>
</template>

<style scoped lang="scss">
.auto-binding-list {
  display: grid;
  gap: 6px;

  &.empty {
    gap: 0;
  }
}

.auto-binding-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.auto-binding-drag-handle {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 32px;
  color: #bfbfbf;
  font-size: 14px;
  cursor: grab;
  border-radius: 4px;
  transition: color 0.2s, background 0.2s;

  &:hover {
    color: #595959;
    background: #f0f0f0;
  }

  &:active {
    cursor: grabbing;
  }
}

.auto-binding-display {
  flex: 1;
  min-width: 0;
}

// 复用 NodeOutputSelector 的 trigger 样式，但不含 clear 按钮
.selector-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  background-color: #F2F4F7;
  border-radius: 6px;
  font-size: 14px;
  min-height: 32px;
}

.trigger-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
}

.trigger-icon {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #ffffff;
}

// Sortable 拖拽状态样式
.auto-binding-ghost {
  opacity: 0.4;
  background: #f0f0f0;
  border-radius: 6px;
}

.auto-binding-chosen {
  opacity: 0.8;
}

.auto-binding-drag {
  opacity: 0.9;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border-radius: 6px;
}

.auto-binding-empty {
  padding: 20px 0;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
}
</style>

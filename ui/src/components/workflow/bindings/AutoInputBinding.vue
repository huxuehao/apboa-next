<script setup lang="ts">
import { computed, inject, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import type { ComputedRef } from 'vue'
import { HolderOutlined, SearchOutlined } from '@ant-design/icons-vue'
import Sortable from 'sortablejs'
import IconFont from '@/components/common/IconFont.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowInputConfig, WorkflowOutputConfig } from '@/types/workflow'
import PanelSection from '@/components/workflow/panels/shared/PanelSection.vue'
import { getNodeIconName } from '@/config/workflow/common'

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

function formatNodeOutputLabel(node: WorkflowFlowNode, outputName?: string): string {
  const outputs = getNodeOutputs(node)
  const output = outputName ? outputs.find((o) => o.name === outputName) : outputs[0]
  if (!output) return node.data.label || node.id
  return `${node.data.label || node.id}  ›  ${output.name}`
}

const props = defineProps<{
  modelValue?: WorkflowInputConfig[]
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  currentNodeId: string
  draggable: boolean
  title?: string
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
  outputs: WorkflowOutputConfig[]
  selectedOutputName: string
}

const displayItems = computed<DisplayItem[]>(() => {
  const ordered: DisplayItem[] = []
  const seen = new Set<string>()

  for (const binding of bindings.value) {
    if (!binding.nodeId || seen.has(binding.nodeId)) continue
    const node = upstreamNodes.value.find((n) => n.id === binding.nodeId)
    if (node) {
      seen.add(binding.nodeId)
      const outputs = getNodeOutputs(node)
      const outputName = binding.outputName || outputs[0]?.name || ''
      ordered.push({
        nodeId: node.id,
        label: formatNodeOutputLabel(node, outputName),
        type: node.data.type,
        color: node.data.schema?.color || '#1677ff',
        outputs,
        selectedOutputName: outputName,
      })
    }
  }

  for (const node of upstreamNodes.value) {
    if (!seen.has(node.id)) {
      const outputs = getNodeOutputs(node)
      const outputName = outputs[0]?.name || ''
      ordered.push({
        nodeId: node.id,
        label: formatNodeOutputLabel(node, outputName),
        type: node.data.type,
        color: node.data.schema?.color || '#1677ff',
        outputs,
        selectedOutputName: outputName,
      })
    }
  }

  return ordered
})

// 输出选择弹窗状态
const openOutputPopoverId = ref<string | null>(null)
const outputSearchText = ref('')

const activeOutputFiltered = computed(() => {
  if (!openOutputPopoverId.value) return []
  const node = upstreamNodes.value.find((n) => n.id === openOutputPopoverId.value)
  if (!node) return []
  const outputs = getNodeOutputs(node)
  const query = outputSearchText.value.trim().toLowerCase()
  if (!query) return outputs
  return outputs.filter((o) => o.name.toLowerCase().includes(query))
})

function handlePopoverToggle(nodeId: string, open: boolean) {
  openOutputPopoverId.value = open ? nodeId : null
  if (!open) outputSearchText.value = ''
}

function updateOutputName(nodeId: string, outputName: string) {
  const current = props.modelValue || []
  const next = current.map((cfg) =>
    cfg.nodeId === nodeId ? { ...cfg, outputName } : cfg,
  )
  syncing = true
  emit('update:modelValue', next)
  nextTick(() => { syncing = false })
}

function selectOutput(nodeId: string, outputName: string) {
  updateOutputName(nodeId, outputName)
  openOutputPopoverId.value = null
  outputSearchText.value = ''
}

watch(openOutputPopoverId, (val) => {
  if (!val) outputSearchText.value = ''
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
        const nodeForOutput = props.nodes.find((n) => n.id === nodeId)
        const firstOutputName = nodeForOutput ? getNodeOutputs(nodeForOutput)[0]?.name : undefined
        filtered.push({ name: 'input_' + nodeId, sourceType: 'NODE_OUTPUT', nodeId, outputName: firstOutputName })
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
        outputName: item.selectedOutputName,
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
  <PanelSection :title="title || '输入节点'">
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

        <!-- 多输出节点：可点击切换输出 -->
        <APopover
          v-if="item.outputs.length > 1"
          :open="openOutputPopoverId === item.nodeId"
          trigger="click"
          placement="bottomLeft"
          :overlay-inner-style="{ padding: 0 }"
          @update:open="handlePopoverToggle(item.nodeId, $event)"
        >
          <div class="auto-binding-display selector-trigger">
            <span class="trigger-icon">
              <IconFont
                :name="getNodeIconName(item.type)"
                :size="14"
                :color="item.color"
              />
              <span class="trigger-label">{{ item.label }}</span>
            </span>
          </div>
          <template #content>
            <div class="output-selector-dropdown">
              <div class="dropdown-search">
                <span class="search-icon"><SearchOutlined /></span>
                <input
                  v-model="outputSearchText"
                  type="text"
                  class="search-input"
                  placeholder="搜索输出名..."
                  @click.stop
                />
              </div>
              <div class="dropdown-list" :class="{ empty: !activeOutputFiltered.length }">
                <div
                  v-for="output in activeOutputFiltered"
                  :key="output.name"
                  class="output-row"
                  :class="{ selected: item.selectedOutputName === output.name }"
                  @click="selectOutput(item.nodeId, output.name)"
                >
                  <span class="output-row-text">{{ output.name }}&ensp;·&ensp;{{ output.type || 'Object' }}</span>
                  <span class="output-row-desc">{{ output.description || '无描述' }}</span>
                </div>
                <div v-if="!activeOutputFiltered.length" class="dropdown-empty">
                  无匹配的输出
                </div>
              </div>
            </div>
          </template>
        </APopover>

        <!-- 单输出节点：静态展示 -->
        <div v-else class="auto-binding-display selector-trigger">
          <span class="trigger-icon">
            <IconFont
              :name="getNodeIconName(item.type)"
              :size="14"
              :color="item.color"
            />
            <span class="trigger-label">{{ item.label }}</span>
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
  grid-template-columns: minmax(0, 1fr);
  gap: 6px;
  min-width: 0;
  overflow: hidden;

  &.empty {
    gap: 0;
  }
}

.auto-binding-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
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
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 2px 8px;
  background-color: #F2F4F7;
  border-radius: 6px;
  font-size: 14px;
  min-height: 32px;
}

.trigger-icon {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  overflow: hidden;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #ffffff;
}

.trigger-label {
  display: block;
  flex: 1;
  min-width: 0;
  margin-left: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
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

// 输出选择器弹窗样式
.output-selector-dropdown {
  width: 320px;
  padding: 8px;
}

.dropdown-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
  border-radius: 8px 8px 0 0;
}

.search-icon {
  color: #bfbfbf;
  font-size: 14px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  color: #262626;
  background: transparent;

  &::placeholder {
    color: #bfbfbf;
  }
}

.dropdown-list {
  max-height: 280px;
  overflow-y: auto;
  margin-top: 5px;

  &.empty {
    max-height: auto;
  }
}

.output-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: #f5f5f5;
  }

  &.selected {
    background: #f5f5f5;
  }
}

.output-row-text {
  font-size: 13px;
  color: #595959;
  flex-shrink: 0;
}

.output-row-desc {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #a8a8a8;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-empty {
  padding: 24px 16px;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
}
</style>

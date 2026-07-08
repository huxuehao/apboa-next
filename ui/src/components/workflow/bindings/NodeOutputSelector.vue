<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { SearchOutlined, CloseCircleFilled } from '@ant-design/icons-vue'
import IconFont from '@/components/common/IconFont.vue'
import type { WorkflowFlowNode, WorkflowOutputConfig } from '@/types/workflow'
import { getNodeIconName } from '@/config/workflow/common'

const props = defineProps<{
  upstreamNodes: WorkflowFlowNode[]
  nodeId?: string
  outputName?: string
}>()

const emit = defineEmits<{
  select: [payload: { nodeId: string; outputName: string }]
  clear: []
}>()

const popoverOpen = ref(false)
const searchText = ref('')

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

interface OutputItem {
  node: WorkflowFlowNode
  output: WorkflowOutputConfig
}

const outputItems = computed<OutputItem[]>(() => {
  const items: OutputItem[] = []
  for (const node of props.upstreamNodes) {
    for (const output of getNodeOutputs(node)) {
      items.push({ node, output })
    }
  }
  return items
})

const selectedNode = computed(() =>
  props.nodeId ? props.upstreamNodes.find((n) => n.id === props.nodeId) : null,
)

const selectedLabel = computed(() => {
  if (!props.nodeId) return ''
  const node = props.upstreamNodes.find((n) => n.id === props.nodeId)
  if (!node) return ''
  const outputs = getNodeOutputs(node)
  const output = outputs.find((o) => o.name === props.outputName)
  if (!output) return `${node.data.label} · ${props.outputName || 'output'}`
  return `${node.data.label}  ›  ${output.name}`
})

const filteredOutputItems = computed(() => {
  const query = searchText.value.trim().toLowerCase()
  if (!query) return outputItems.value
  return outputItems.value.filter(
    (item) =>
      item.node.data.label.toLowerCase().includes(query) ||
      item.output.name.toLowerCase().includes(query),
  )
})

// Group filtered items by node
const groupedItems = computed(() => {
  const map = new Map<string, { node: WorkflowFlowNode; outputs: WorkflowOutputConfig[] }>()
  for (const item of filteredOutputItems.value) {
    const existing = map.get(item.node.id)
    if (existing) {
      existing.outputs.push(item.output)
    } else {
      map.set(item.node.id, { node: item.node, outputs: [item.output] })
    }
  }
  return [...map.values()]
})

// 区分当前流程和主流程来源
const localGroups = computed(() =>
  groupedItems.value.filter((g) => !(g.node.data as any)._parentSource),
)
const parentGroups = computed(() =>
  groupedItems.value.filter((g) => !!(g.node.data as any)._parentSource),
)

function selectOutput(nodeId: string, outputName: string) {
  emit('select', { nodeId, outputName })
  popoverOpen.value = false
  searchText.value = ''
}

function clearSelection() {
  emit('clear')
}

watch(popoverOpen, (open) => {
  if (!open) searchText.value = ''
})
</script>

<template>
  <APopover
    v-model:open="popoverOpen"
    trigger="click"
    placement="bottomLeft"
    :overlay-inner-style="{ padding: 0 }"
  >
    <div class="selector-trigger" :class="{ placeholder: !selectedLabel }">
      <span v-if="selectedLabel" class="trigger-icon">
        <IconFont v-if="selectedNode" :name="getNodeIconName(selectedNode.data.type)" :size="14" :color="selectedNode.data.schema?.color || '#1677ff'" />
        <span class="trigger-label">{{ selectedLabel }}</span>
      </span>
      <span v-else class="trigger-placeholder">选择节点输出...</span>
      <CloseCircleFilled
        v-if="selectedLabel"
        class="trigger-clear"
        @click.stop="clearSelection"
      />
    </div>

    <template #content>
      <div class="selector-dropdown">
        <div class="dropdown-search">
          <span class="search-icon"><SearchOutlined /></span>
          <input
            v-model="searchText"
            type="text"
            class="search-input"
            placeholder="搜索节点或输出名..."
            @click.stop
          />
        </div>

        <div class="dropdown-list" :class="{ empty: !groupedItems.length }">
          <template v-if="localGroups.length || parentGroups.length">
            <!-- 当前子流程节点 -->
            <div v-for="group in localGroups" :key="group.node.id" class="node-group">
              <div class="node-group-header">
                <IconFont :name="getNodeIconName(group.node.data.type)" :size="14" :color="group.node.data.schema?.color || '#1677ff'" />
                <span class="node-group-name">{{ group.node.data.label }}</span>
              </div>
              <div class="node-group-outputs">
                <div
                  v-for="output in group.outputs"
                  :key="`${group.node.id}-${output.name}`"
                  class="output-row"
                  :class="{ selected: nodeId === group.node.id && outputName === output.name }"
                  :title="`${output.name} · ${output.type} · ${output.description || '无描述' }`"
                  @click="selectOutput(group.node.id, output.name)"
                >
                  <span class="output-row-text">{{ output.name }}&ensp;·&ensp;{{ output.type || 'Object' }}</span>
                  <span class="output-row-desc">{{ output.description || '无描述' }}</span>
                </div>
              </div>
            </div>

            <!-- 分隔线 + 主流程区域 -->
            <template v-if="parentGroups.length">
              <div class="source-divider">
                <span class="source-divider-text">以下输出来自主流程</span>
              </div>
              <div v-for="group in parentGroups" :key="group.node.id" class="node-group parent-section">
                <div class="node-group-header">
                  <IconFont :name="getNodeIconName(group.node.data.type)" :size="14" :color="group.node.data.schema?.color || '#1677ff'" />
                  <span class="node-group-name">{{ group.node.data.label }}</span>
                </div>
                <div class="node-group-outputs">
                  <div
                    v-for="output in group.outputs"
                    :key="`${group.node.id}-${output.name}`"
                    class="output-row"
                    :class="{ selected: nodeId === group.node.id && outputName === output.name }"
                    :title="`${output.name} · ${output.type} · ${output.description || '无描述' }`"
                    @click="selectOutput(group.node.id, output.name)"
                  >
                    <span class="output-row-text">{{ output.name }}&ensp;·&ensp;{{ output.type || 'Object' }}</span>
                    <span class="output-row-desc">{{ output.description || '无描述' }}</span>
                  </div>
                </div>
              </div>
            </template>
          </template>

          <div v-else-if="upstreamNodes.length === 0" class="dropdown-empty">
            当前节点无上游，请先连线
          </div>
          <div v-else class="dropdown-empty">
            无匹配的输出
          </div>
        </div>
      </div>
    </template>
  </APopover>
</template>

<style scoped lang="scss">
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
  cursor: pointer;
  font-size: 14px;
  transition: border-color 0.2s;
  min-height: 32px;
  position: relative;

  &.placeholder {
    color: #bfbfbf;
  }
}

.trigger-clear {
  flex-shrink: 0;
  color: #bfbfbf;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #595959;
  }
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
  flex: 1;
  min-width: 0;
  margin-left: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
}

.trigger-placeholder {
  flex: 1;
  color: #bfbfbf;
}

.selector-dropdown {
  width: 386px;
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
  max-height: 320px;
  overflow-y: auto;

  &.empty {
    max-height: auto;
  }
}

.node-group-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px 4px;
  cursor: default;
  min-width: 0;
  overflow: hidden;
}

.node-group-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.parent-source-tag {
  flex-shrink: 0;
  padding: 0 4px;
  font-size: 10px;
  color: #1677ff;
  background: rgba(22, 119, 255, 0.06);
  border-radius: 2px;
  line-height: 16px;
}

.source-divider {
  display: flex;
  align-items: center;
  padding: 10px 12px 6px;
  margin: 4px 0 0;
  border-top: 1px solid #f0f0f0;
}

.source-divider-text {
  font-size: 11px;
  color: #8c8c8c;
  letter-spacing: 0.3px;
}

.parent-section .node-group-header {
  opacity: 0.85;
}

.node-group-outputs {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.output-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: calc(100% - 18px);
  margin-left: 12px;
  padding: 5px 12px 5px 18px;
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

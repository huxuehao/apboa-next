<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { SearchOutlined, CloseCircleFilled } from '@ant-design/icons-vue'
import IconFont from '@/components/common/IconFont.vue'
import type { WorkflowFlowNode, WorkflowFlowEdge } from '@/types/workflow'
import { getNodeIconName } from '@/config/workflow/common'

const props = defineProps<{
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  currentNodeId: string
  selectedNodeId?: string
}>()

const emit = defineEmits<{
  select: [nodeId: string]
  clear: []
}>()

const popoverOpen = ref(false)
const searchText = ref('')

// 下游节点：仅直接相连的下游节点（source 为当前节点）
const downstreamNodes = computed(() => {
  if (!props.edges.length || !props.currentNodeId) return []

  const targetIds = new Set(
    props.edges
      .filter((e) => e.source === props.currentNodeId)
      .map((e) => e.target),
  )

  return props.nodes.filter((n) => targetIds.has(n.id))
})

const selectedNode = computed(() =>
  props.selectedNodeId ? props.nodes.find((n) => n.id === props.selectedNodeId) : null,
)

const selectedLabel = computed(() => {
  if (!selectedNode.value) return ''
  return selectedNode.value.data.label
})

const filteredNodes = computed(() => {
  const query = searchText.value.trim().toLowerCase()
  if (!query) return downstreamNodes.value
  return downstreamNodes.value.filter((n) =>
    n.data.label.toLowerCase().includes(query) ||
    n.data.type.toLowerCase().includes(query),
  )
})

function selectNode(nodeId: string) {
  emit('select', nodeId)
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
      <span v-else class="trigger-placeholder">选择下游节点...</span>
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
            placeholder="搜索节点名称或类型..."
            @click.stop
          />
        </div>

        <div class="dropdown-list" :class="{ empty: !filteredNodes.length }">
          <template v-if="filteredNodes.length">
            <div
              v-for="node in filteredNodes"
              :key="node.id"
              class="node-row"
              :class="{ selected: selectedNodeId === node.id }"
              @click="selectNode(node.id)"
            >
              <IconFont :name="getNodeIconName(node.data.type)" :size="14" :color="node.data.schema?.color || '#1677ff'" />
              <span class="node-row-name">{{ node.data.label }}</span>
              <span class="node-row-type">{{ node.data.type }}</span>
            </div>
          </template>

          <div v-else-if="downstreamNodes.length === 0" class="dropdown-empty">
            当前节点无下游，请先连线
          </div>
          <div v-else class="dropdown-empty">
            无匹配的节点
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
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 5px;
  max-height: 280px;
  overflow-y: auto;

  &.empty {
    max-height: auto;
  }
}

.node-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
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

.node-row-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-row-type {
  flex-shrink: 0;
  font-size: 12px;
  color: #a8a8a8;
}

.dropdown-empty {
  padding: 24px 16px;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
}
</style>

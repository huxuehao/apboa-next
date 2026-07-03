<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { SearchOutlined, CloseCircleFilled } from '@ant-design/icons-vue'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'
import type { WorkflowFlowNode, WorkflowFlowEdge } from '@/types/workflow'

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

// 上游节点：直接连接到当前节点的上游节点（target 为当前节点）
const upstreamNodes = computed(() => {
  if (!props.edges.length || !props.currentNodeId) return []

  const sourceIds = new Set(
    props.edges
      .filter((e) => e.target === props.currentNodeId)
      .map((e) => e.source),
  )

  return props.nodes.filter((n) => sourceIds.has(n.id))
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
  if (!query) return upstreamNodes.value
  return upstreamNodes.value.filter((n) =>
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
      <span v-if="selectedLabel" class="trigger-text">
        <span class="trigger-icon">
          <IconFont v-if="selectedNode" :name="getIconName(selectedNode.data.type)" :size="14" :color="selectedNode.data.schema?.color || '#1677ff'" />
          <span style="margin-left: 4px;">{{ selectedLabel }}</span>
        </span>
      </span>
      <span v-else class="trigger-placeholder">选择上游节点...</span>
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
              <IconFont :name="getIconName(node.data.type)" :size="14" :color="node.data.schema?.color || '#1677ff'" />
              <span class="node-row-name">{{ node.data.label }}</span>
              <span class="node-row-type">{{ node.data.type }}</span>
            </div>
          </template>

          <div v-else-if="upstreamNodes.length === 0" class="dropdown-empty">
            当前节点无上游，请先连线
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
  padding: 6px 8px;
  border-radius: 6px;
  background-color: #ffffff;
}

.trigger-text {
  flex: 1;
  min-width: 0;
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

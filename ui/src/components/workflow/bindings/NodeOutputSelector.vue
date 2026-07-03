<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { SearchOutlined, CloseCircleFilled } from '@ant-design/icons-vue'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'
import type { WorkflowFlowNode, WorkflowOutputConfig } from '@/types/workflow'

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
      <span v-if="selectedLabel" class="trigger-text">
        <span class="trigger-icon">
          <IconFont v-if="selectedNode" :name="getIconName(selectedNode.data.type)" :size="14" :color="selectedNode.data.schema?.color || '#1677ff'" />
          <span style="margin-left: 4px;">{{ selectedLabel }}</span>
        </span>
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
          <template v-if="groupedItems.length">
            <div v-for="group in groupedItems" :key="group.node.id" class="node-group">
              <div class="node-group-header">
                <IconFont :name="getIconName(group.node.data.type)" :size="14" :color="group.node.data.schema?.color || '#1677ff'" />
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
  padding: 2px 8px;
  background-color: #F2F4F7;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: border-color 0.2s;
  min-height: 32px;
  position: relative;

  &:hover {
    border-color: #1677ff;
  }

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
}

.node-group-name {
  font-size: 13px;
  color: #262626;
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
    background: #e6f4ff;
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

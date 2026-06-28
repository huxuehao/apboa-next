<script setup lang="ts">
import { computed, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { buildNodeSummary } from '@/config/workflow/nodeSchemas'
import type { FlowNodeData, WorkflowResourceMaps } from '@/types/workflow'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'

const props = defineProps<{
  data: FlowNodeData & { resources?: WorkflowResourceMaps }
  selected?: boolean
}>()

const emit = defineEmits<{
  'add-node': [payload: { x: number; y: number }]
}>()

const hovered = ref(false)

function onAddClick(event: MouseEvent) {
  emit('add-node', { x: event.clientX, y: event.clientY })
}


/**
 * 节点类型 → iconfont 图标名称映射
 */
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

/**
 * 根据节点类型获取对应的 iconfont 图标名称
 */
function getNodeIconName(type: string): IconName {
  return nodeIconMap[type] || 'nodecode'
}

const color = computed(() => props.data.schema?.color || '#1677ff')
const summary = computed(() => buildNodeSummary(props.data.schema, props.data.config || {}, props.data.resources))
const isStart = computed(() => props.data.type === 'START')
const isEnd = computed(() => props.data.type === 'END')
const branchHandles = computed(() => props.data.schema?.branchHandles || [])
</script>

<template>
  <div class="graph-node" :class="{ selected, hovered, [`status-${data.status || 'IDLE'}`]: true }"
    @mouseenter="hovered = true" @mouseleave="hovered = false">
    <Handle v-if="!isStart" type="target" :position="Position.Left" class="node-handle target-handle" id="input" />

    <div class="node-top">
      <div class="node-avatar" :style="{ backgroundColor: `${color}CC` }">
        <IconFont :name="getNodeIconName(data.type)" :size="17" color="#ffffff" />
      </div>
      <div class="node-title" :title="data.label">{{ data.label }}</div>
      <span class="node-state" />
    </div>

    <div v-if="summary.length" class="node-bottom">
      <div class="summary-list">
        <div v-for="(item, index) in summary" :key="index" class="summary-item">
          {{ item.label }}: {{ item.value }}
        </div>
      </div>
    </div>

    <template v-if="!isEnd && branchHandles.length">
      <Handle
        v-for="(handle, index) in branchHandles"
        :key="handle.id"
        type="source"
        :id="handle.id"
        :position="Position.Right"
        class="node-handle branch-handle"
        :style="{ top: `${42 + index * 28}px` }"
        @click="onAddClick"
      />
    </template>
    <Handle v-else-if="!isEnd" type="source" :position="Position.Right" class="node-handle source-handle" id="output" @click="onAddClick" />
  </div>
</template>

<style scoped lang="scss">
.graph-node {
  width: 236px;
  min-height: 0;
  border-radius: 12px;
  background: #fff;
  color: #262626;
  border: 1px solid transparent;
  box-shadow: 0px 2px 12px rgba(131, 131, 132, 0.25);
}

.graph-node.selected {
  border: 1px solid #1677ff;
}

.node-top {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 10px;
  gap: 5px;
  align-items: center;
  padding: 12px 12px 10px;
}

.node-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
}

.node-avatar :deep(.iconfont) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.node-title {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-state {
  width: 9px;
  height: 9px;
  border: 1px solid #d9d9d9;
  border-radius: 50%;
  background: #f5f5f5;
}

.node-bottom {
  min-height: 50px;
  padding: 10px 12px 12px;
  background: transparent;
}

.summary-list {
  display: grid;
  gap: 6px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  color: #595959;
  font-size: 12px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  font-size: 12px;
  color: #bfbfbf;
}

.node-handle {
  width: 8px;
  height: 8px;
  border: none;
  background: #1677ff;
  transition: width 0.15s, height 0.15s;
}

/* hover 时源端 handle 放大并叠加 + 号圆圈（CSS 纯绘制，不依赖字体基线） */
.graph-node.hovered .source-handle,
.graph-node.hovered .branch-handle {
  width: 14px;
  height: 14px;
}

.graph-node.hovered .source-handle::after,
.graph-node.hovered .branch-handle::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 18 18'%3E%3Crect x='3' y='8' width='12' height='2' rx='1' fill='white'/%3E%3Crect x='8' y='3' width='2' height='12' rx='1' fill='white'/%3E%3C/svg%3E") no-repeat center / 18px 18px,
    #1677ff;
  pointer-events: none;
}

.branch-handle {
  right: -4px;
}

.status-SUCCESS .node-state {
  border-color: #52c41a;
  background: #52c41a;
}

.status-FAIL .node-state,
.status-INVALID .node-state {
  border-color: #ff4d4f;
  background: #ff4d4f;
}

.status-RUNNING .node-state {
  border-color: #1677ff;
  background: #1677ff;
}
</style>

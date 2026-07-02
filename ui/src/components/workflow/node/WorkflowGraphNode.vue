<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { FlowNodeData, WorkflowResourceMaps } from '@/types/workflow'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'

const props = defineProps<{
  data: FlowNodeData & { resources?: WorkflowResourceMaps }
  selected?: boolean
  locked?: boolean
}>()

const emit = defineEmits<{
  'add-node': [payload: { x: number; y: number; sourceHandle: string }]
}>()

const hovered = ref(false)
const addModifierPressed = ref(false)

function isAddModifierEvent(event: MouseEvent) {
  return event.ctrlKey || event.metaKey || addModifierPressed.value
}

function onAddClick(event: MouseEvent, sourceHandle: string) {
  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation()
  if (props.locked) return
  if (!isAddModifierEvent(event)) return
  emit('add-node', { x: event.clientX, y: event.clientY, sourceHandle })
}

function stopHandleAddEvent(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation()
}

function updateAddModifier(event: KeyboardEvent) {
  addModifierPressed.value = event.ctrlKey || event.metaKey
}

function clearAddModifier() {
  addModifierPressed.value = false
}

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

function getNodeIconName(type: string): IconName {
  return nodeIconMap[type] || 'nodecode'
}

const color = computed(() => props.data.schema?.color || '#1677ff')
const showSummary = computed(() => props.data.schema?.showSummary ?? true)
const summaryComponent = computed(() => {
  const name = props.data.schema?.summaryComponent
  if (!name) return null
  return defineAsyncComponent(() => import(`./summaries/${name}.vue`))
})
const isStart = computed(() => props.data.type === 'START')
const isEnd = computed(() => props.data.type === 'END')
const branchHandles = computed(() => props.data.schema?.branchHandles || [])

onMounted(() => {
  window.addEventListener('keydown', updateAddModifier)
  window.addEventListener('keyup', updateAddModifier)
  window.addEventListener('blur', clearAddModifier)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', updateAddModifier)
  window.removeEventListener('keyup', updateAddModifier)
  window.removeEventListener('blur', clearAddModifier)
})
</script>

<template>
  <div
    class="graph-node"
    :class="{ selected, hovered, [`status-${data.status || 'IDLE'}`]: true }"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <div v-if="hovered && !locked" class="node-operation-hint">按住 Ctrl 添加节点</div>
    <Handle
      v-if="!isStart"
      type="target"
      :position="Position.Left"
      class="node-handle target-handle"
      id="input"
    />
    <div class="node-top">
      <div class="node-avatar" :style="{ backgroundColor: `${color}CC` }">
        <IconFont :name="getNodeIconName(data.type)" :size="17" color="#ffffff" />
      </div>
      <div class="node-title" :title="data.label">{{ data.label }}</div>
      <span class="node-state" />
    </div>
    <div v-if="showSummary" class="node-bottom">
      <Suspense>
        <component
          v-if="summaryComponent"
          :is="summaryComponent"
          :config="data.config || {}"
          :resources="data.resources"
          :schema="data.schema"
        />
        <div v-else class="summary-empty">点击配置节点参数</div>
      </Suspense>
    </div>
    <template v-if="!isEnd && branchHandles.length">
      <div
        v-for="(handle, idx) in branchHandles"
        :key="handle.id"
        class="branch-port"
        :style="{ top: `${42 + idx * 28}px` }"
      >
        <Handle
          type="source"
          :id="handle.id"
          :position="Position.Right"
          class="node-handle branch-handle"
        />
        <button
          v-if="hovered && addModifierPressed && !locked"
          type="button"
          class="handle-add-button"
          :aria-label="`从 ${handle.label || handle.id} 分支添加节点`"
          @pointerdown="stopHandleAddEvent"
          @pointerup="stopHandleAddEvent"
          @mousedown="stopHandleAddEvent"
          @mouseup="stopHandleAddEvent"
          @click="(event) => onAddClick(event, handle.id)"
        />
      </div>
    </template>
    <div v-else-if="!isEnd" class="source-port">
      <Handle
        type="source"
        :position="Position.Right"
        class="node-handle source-handle"
        id="output"
      />
      <button
        v-if="hovered && addModifierPressed && !locked"
        type="button"
        class="handle-add-button"
        aria-label="添加下一个节点"
        @pointerdown="stopHandleAddEvent"
        @pointerup="stopHandleAddEvent"
        @mousedown="stopHandleAddEvent"
        @mouseup="stopHandleAddEvent"
        @click="(event) => onAddClick(event, 'output')"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.graph-node {
  position: relative;
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
.node-operation-hint {
  position: absolute;
  left: 8px;
  right: 8px;
  top: -19px;
  z-index: 2;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid #f2f2f2;
  border-bottom: 0;
  border-radius: 12px 12px 0 0;
  background: #f7f8fa;
  color: #aaaaaa;
  font-size: 9px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
  box-shadow: 0 -1px 4px rgba(30, 41, 59, 0.04);
  pointer-events: none;
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
  padding: 0 10px 12px 12px;
  background: transparent;
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
  transition:
    width 0.15s,
    height 0.15s;
}
.graph-node.hovered .source-handle,
.graph-node.hovered .branch-handle {
  width: 14px;
  height: 14px;
}
.source-port,
.branch-port {
  position: absolute;
  right: 0;
  z-index: 3;
}
.source-port {
  top: 50%;
}
.branch-port {
  width: 0;
  height: 0;
}
.source-port .source-handle,
.branch-port .branch-handle {
  right: -4px;
}
.handle-add-button {
  position: absolute;
  z-index: 4;
  right: -13px;
  top: 50%;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 18 18'%3E%3Crect x='3' y='8' width='12' height='2' rx='1' fill='white'/%3E%3Crect x='8' y='3' width='2' height='12' rx='1' fill='white'/%3E%3C/svg%3E")
      no-repeat center / 18px 18px,
    #1677ff;
  cursor: pointer;
  transform: translateY(-50%);
  box-shadow: 0 2px 6px rgba(22, 119, 255, 0.28);
}
.handle-add-button:hover {
  background-color: #4096ff;
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

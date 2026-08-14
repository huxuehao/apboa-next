<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import type { FlowNodeData, WorkflowResourceMaps } from '@/types/workflow'
import IconFont from '@/components/common/IconFont.vue'
import { getNodeIconName } from '@/config/workflow/common'
import { isNodeRunnable } from '@/config/workflow/nodeRun'
import { hasOutgoingEdge } from '@/utils/workflow/edgeRules'

const props = defineProps<{
  id: string
  data: FlowNodeData & { resources?: WorkflowResourceMaps }
  selected?: boolean
  locked?: boolean
}>()

const emit = defineEmits<{
  'add-node': [payload: { x: number; y: number; sourceHandle: string }]
  run: []
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

const color = computed(() => props.data.schema?.color || '#1677ff')
const showSummary = computed(() => props.data.schema?.showSummary ?? true)
const runnable = computed(() => isNodeRunnable(props.data.type))

function onRunClick() {
  if (props.locked || !runnable.value) return
  emit('run')
}
// 缓存 defineAsyncComponent 结果，相同组件名始终返回同一引用，防止 Vue 因引用变化而销毁重建组件
const asyncSummaryCache = new Map<string, ReturnType<typeof defineAsyncComponent>>()

const summaryComponent = computed(() => {
  const name = props.data.schema?.summaryComponent
  if (!name) return null
  if (!asyncSummaryCache.has(name)) {
    asyncSummaryCache.set(name, defineAsyncComponent(() => import(`./summaries/${name}.vue`)))
  }
  return asyncSummaryCache.get(name)!
})
const isStart = computed(() => props.data.type === 'START')
const isEnd = computed(() => props.data.type === 'END')
const branchHandles = computed(() => props.data.schema?.branchHandles || [])

// 从 VueFlow 内部 store 读取连线，用于判断输出点是否已被占用
const { edges } = useVueFlow()
const multipleOutputs = computed(() => Boolean(props.data.schema?.multipleOutputs))

/** 指定输出点是否还能新增连线：多输出节点不限制，单输出节点每个输出点最多一条 */
function canAddFromHandle(handleId: string) {
  return multipleOutputs.value || !hasOutgoingEdge(edges.value, props.id, handleId)
}

/** 是否存在可用输出点，决定“按住 Ctrl 添加节点”提示是否展示 */
const canAddAnyOutput = computed(() => {
  if (isEnd.value) return false
  if (multipleOutputs.value) return true
  const handleIds = branchHandles.value.length ? branchHandles.value.map((item) => item.id) : ['output']
  return handleIds.some((handleId) => !hasOutgoingEdge(edges.value, props.id, handleId))
})

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
    <div v-if="hovered && !locked && canAddAnyOutput" class="node-operation-hint">按住 Ctrl 添加节点</div>
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
      <div class="node-actions">
        <ATooltip title="运行此节点" v-if="runnable && hovered && !locked">
          <button
            type="button"
            class="node-run-button"
            @pointerdown.stop
            @pointerup.stop
            @mousedown.stop
            @mouseup.stop
            @click.stop="onRunClick"
          >
            <span class="play-triangle" />
          </button>
        </ATooltip>
        <span v-else class="node-state" />
      </div>
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
          v-if="hovered && addModifierPressed && !locked && canAddFromHandle(handle.id)"
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
        v-if="hovered && addModifierPressed && !locked && canAddFromHandle('output')"
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
  box-shadow: 0px 2px 12px rgba(131, 131, 132, 0.05);
  border: 1px solid transparent;
  transition: 0.15s;
}
.graph-node.hovered {
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
  grid-template-columns: 32px minmax(0, 1fr) 20px;
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
  border-radius: 8px;
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
.node-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
}
.node-run-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.play-triangle {
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 5.5px 0 5.5px 8px;
  border-color: transparent transparent transparent #9da0ad;
  /* 关键：给边框添加圆角，使三角的边有弧度 */
  border-radius: 20%;
  margin-left: 1px;
  transition: border-color 0.15s;
}
.node-run-button:hover .play-triangle {
  border-color: transparent transparent transparent #6a6e7b;
}
.node-state {
  width: 9px;
  height: 9px;
  border-radius: 50%;
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

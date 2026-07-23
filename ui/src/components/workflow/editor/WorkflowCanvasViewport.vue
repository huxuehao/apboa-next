<script setup lang="ts">
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { VueFlow, useVueFlow, type Connection, type GraphNode } from '@vue-flow/core'
import WorkflowGraphEdge from '@/components/workflow/edge/WorkflowGraphEdge.vue'
import WorkflowGraphNode from '@/components/workflow/node/WorkflowGraphNode.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode } from '@/types/workflow'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'

const nodes = defineModel<WorkflowFlowNode[]>('nodes', { required: true })
const edges = defineModel<WorkflowFlowEdge[]>('edges', { required: true })

const props = defineProps<{
  readonly: boolean
}>()

const emit = defineEmits<{
  selectNode: [nodeId: string | null]
  nodeContext: [payload: { nodeId: string; x: number; y: number }]
  paneClick: []
  showLibrary: [payload: { sourceNodeId: string; sourceHandle: string; x: number; y: number }]
  showLibraryFromEdge: [payload: { edgeId: string; x: number; y: number }]
  deleteNodes: [nodeIds: string[]]
}>()

const flow = useVueFlow()
const { viewport } = flow

// ========== 对齐辅助线 ==========
interface AlignGuide {
  orientation: 'vertical' | 'horizontal'
  position: number
  start: number
  end: number
}

const alignGuides = ref<AlignGuide[]>([])
const SNAP_THRESHOLD = 5

/** 将流坐标对齐引导线转换为屏幕坐标（相对于画布容器） */
const screenGuides = computed(() => {
  const vp = viewport.value
  return alignGuides.value.map((g) => {
    if (g.orientation === 'vertical') {
      const left = g.position * vp.zoom + vp.x
      const top = g.start * vp.zoom + vp.y
      const height = Math.max((g.end - g.start) * vp.zoom, 1)
      return {
        key: `v-${Math.round(g.position)}-${Math.round(g.start)}`,
        class: 'align-guide-overlay align-guide-vertical',
        style: {
          left: `${left}px`,
          top: `${top}px`,
          height: `${height}px`,
        },
      }
    }
    const left = g.start * vp.zoom + vp.x
    const top = g.position * vp.zoom + vp.y
    const width = Math.max((g.end - g.start) * vp.zoom, 1)
    return {
      key: `h-${Math.round(g.position)}-${Math.round(g.start)}`,
      class: 'align-guide-overlay align-guide-horizontal',
      style: {
        left: `${left}px`,
        top: `${top}px`,
        width: `${width}px`,
      },
    }
  })
})

function computeAlignment(draggedNode: GraphNode) {
  const guides: AlignGuide[] = []
  let snapX = 0
  let snapY = 0

  const dw = draggedNode.dimensions.width || 0
  const dh = draggedNode.dimensions.height || 0
  if (!dw || !dh) return { guides, snapX, snapY }

  const d = {
    x: draggedNode.position.x,
    y: draggedNode.position.y,
    cx: draggedNode.position.x + dw / 2,
    cy: draggedNode.position.y + dh / 2,
    r: draggedNode.position.x + dw,
    b: draggedNode.position.y + dh,
  }

  const others: GraphNode[] = []
  for (const n of nodes.value as GraphNode[]) {
    if (n.id !== draggedNode.id && n.dimensions.width > 0 && n.dimensions.height > 0) {
      others.push(n)
    }
  }

  for (const other of others) {
    const ow = other.dimensions.width
    const oh = other.dimensions.height
    const o = {
      x: other.position.x,
      y: other.position.y,
      cx: other.position.x + ow / 2,
      cy: other.position.y + oh / 2,
      r: other.position.x + ow,
      b: other.position.y + oh,
    }

    const minY = Math.min(d.y, o.y)
    const maxB = Math.max(d.b, o.b)
    const minX = Math.min(d.x, o.x)
    const maxR = Math.max(d.r, o.r)

    const vPairs: [number, number][] = [
      [d.x, o.x],
      [d.cx, o.cx],
      [d.r, o.r],
    ]
    for (const [dv, ov] of vPairs) {
      const diff = dv - ov
      if (Math.abs(diff) < SNAP_THRESHOLD) {
        guides.push({ orientation: 'vertical', position: ov, start: minY, end: maxB })
        if (!snapX || Math.abs(diff) < Math.abs(snapX)) snapX = -diff
      }
    }

    const hPairs: [number, number][] = [
      [d.y, o.y],
      [d.cy, o.cy],
      [d.b, o.b],
    ]
    for (const [dv, ov] of hPairs) {
      const diff = dv - ov
      if (Math.abs(diff) < SNAP_THRESHOLD) {
        guides.push({ orientation: 'horizontal', position: ov, start: minX, end: maxR })
        if (!snapY || Math.abs(diff) < Math.abs(snapY)) snapY = -diff
      }
    }
  }

  return { guides: deduplicateGuides(guides), snapX, snapY }
}

function deduplicateGuides(guides: AlignGuide[]): AlignGuide[] {
  const seen = new Set<string>()
  return guides.filter((g) => {
    const key = `${g.orientation}-${Math.round(g.position)}-${Math.round(g.start)}-${Math.round(g.end)}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function onNodeDragStart({ node }: { node: GraphNode }) {
  // if (props.readonly) return
  // emit('selectNode', node.id)
}

function onNodeDrag({ node }: { node: GraphNode }) {
  if (props.readonly) return
  const { guides, snapX, snapY } = computeAlignment(node)
  alignGuides.value = guides

  // 应用吸附：将节点拉近到最近的参考对齐线
  if (snapX !== 0 || snapY !== 0) {
    node.position = {
      x: node.position.x + snapX,
      y: node.position.y + snapY,
    }
  }
}

function onNodeDragStop() {
  alignGuides.value = []
  // 双帧兜底：处理 VueFlow 在 stop 之后又同步触发 drag 的边界情况
  requestAnimationFrame(() => {
    alignGuides.value = []
  })
}

/** 兜底清除辅助线（处理 node-drag-stop 未触发的异常场景） */
function clearGuides() {
  alignGuides.value = []
}

// 全局安全网：捕获阶段监听，确保在 VueFlow stopPropagation 之前拦截
function onKeyDown(event: KeyboardEvent) {
  if (event.key !== 'Backspace' && event.key !== 'Delete') return
  if (props.readonly) return
  // 只在画布区域有焦点时才响应，避免与输入框等控件冲突
  const canvas = document.querySelector('.workflow-canvas-viewport')
  if (!canvas || !canvas.contains(document.activeElement)) return
  const tag = (document.activeElement as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  const selectedIds = (nodes.value as GraphNode[]).filter((n) => n.selected).map((n) => n.id)
  if (!selectedIds.length) return
  event.preventDefault()
  emit('deleteNodes', selectedIds)
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown, true)
  window.addEventListener('mouseup', clearGuides, true)
  window.addEventListener('pointerup', clearGuides, true)
  window.addEventListener('blur', clearGuides)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown, true)
  window.removeEventListener('mouseup', clearGuides, true)
  window.removeEventListener('pointerup', clearGuides, true)
  window.removeEventListener('blur', clearGuides)
})

function getSourceHandles(nodeId: string) {
  const node = nodes.value.find((item) => item.id === nodeId)
  if (!node || node.data.type === 'END') return []
  const branchHandles = node.data.schema?.branchHandles || []
  return branchHandles.length ? branchHandles.map((item) => item.id) : ['output']
}

function onConnect(connection: Connection) {
  if (props.readonly) return
  if (!connection.source || !connection.target || connection.source === connection.target) return
  const sourceHandles = getSourceHandles(connection.source)
  const sourceHandle = connection.sourceHandle || (sourceHandles.length === 1 ? sourceHandles[0] : '')
  const targetHandle = connection.targetHandle || 'input'
  if (!sourceHandle || !sourceHandles.includes(sourceHandle) || targetHandle !== 'input') return
  const edgeExists = edges.value.some(
    (edge) =>
      edge.source === connection.source &&
      edge.target === connection.target &&
      (edge.sourceHandle || 'output') === sourceHandle &&
      (edge.targetHandle || 'input') === targetHandle,
  )
  if (edgeExists) return
  flow.addEdges([
    {
      id: `edge-${connection.source}-${sourceHandle}-${connection.target}-${Date.now()}`,
      source: connection.source,
      target: connection.target,
      sourceHandle,
      targetHandle,
      type: 'workflow',
    },
  ])
}

function onNodeClick({ node }: { node: GraphNode }) {
  if (props.readonly) return
  emit('selectNode', node.id)
}

function onNodeContextMenu({ node, event }: { node: GraphNode; event: MouseEvent | TouchEvent }) {
  if (props.readonly) return
  event?.preventDefault()
  emit('nodeContext', { nodeId: node.id, x: (event as MouseEvent).clientX || 0, y: (event as MouseEvent).clientY || 0 })
}

function onPaneClick() {
  if (props.readonly) return
  clearGuides()
  emit('selectNode', null)
  emit('paneClick')
}

function onNodeAddClick(nodeId: string, payload: { x: number; y: number; sourceHandle: string }) {
  if (props.readonly) return
  emit('showLibrary', { sourceNodeId: nodeId, ...payload })
}

function onEdgeAddClick(payload: { edgeId: string; x: number; y: number }) {
  if (props.readonly) return
  emit('showLibraryFromEdge', payload)
}

function addAtCenter() {
  const rect = document.querySelector('.workflow-canvas-viewport')?.getBoundingClientRect()
  const point = {
    x: (rect?.width || window.innerWidth) / 2,
    y: (rect?.height || window.innerHeight) / 2,
  }
  return flow.project(point)
}

function fitAll() {
  flow.fitView({ padding: 0.2, duration: 200 })
}

function zoomInCanvas() {
  flow.zoomIn({ duration: 120 })
}

function zoomOutCanvas() {
  flow.zoomOut({ duration: 120 })
}

function resetZoom() {
  flow.setViewport({ x: 0, y: 0, zoom: 1 }, { duration: 160 })
}

function fitNode(nodeId: string) {
  flow.fitView({ nodes: [nodeId], padding: 0.4, duration: 200 })
}

function getViewport() {
  return { ...viewport.value }
}

function restoreViewport(vp: { x: number; y: number; zoom: number }) {
  flow.setViewport(vp, { duration: 0 })
}

defineExpose({ addAtCenter, fitAll, zoomInCanvas, zoomOutCanvas, resetZoom, fitNode, getViewport, restoreViewport })
</script>

<template>
  <section class="workflow-canvas-viewport">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      fit-view-on-init
      :nodes-draggable="!readonly"
      :nodes-connectable="!readonly"
      :edges-updatable="!readonly"
      :delete-key-code="[]"
      :default-edge-options="{ animated: false, type: 'workflow' }"
      @connect="onConnect"
      @node-click="onNodeClick"
      @node-context-menu="onNodeContextMenu"
      @pane-click="onPaneClick"
      @node-drag-start="onNodeDragStart"
      @node-drag="onNodeDrag"
      @node-drag-stop="onNodeDragStop"
    >
      <template #node-workflow="slotProps">
        <WorkflowGraphNode
          v-bind="slotProps"
          :locked="readonly"
          @add-node="(pos: any) => onNodeAddClick(String(slotProps.id), pos)"
        />
      </template>
      <template #edge-workflow="slotProps">
        <WorkflowGraphEdge v-bind="slotProps" :locked="readonly" @add-node="onEdgeAddClick" />
      </template>
      <Background :gap="18" :size="2" color="#DBDCDE" />
      <MiniMap
        pannable
        zoomable
        class="workflow-minimap"
        mask-color="rgba(245, 245, 245, 0.62)"
        node-color="#d9d9d9"
        node-stroke-color="#8c8c8c"
      />
    </VueFlow>

    <!-- 对齐辅助线覆盖层（屏幕坐标，在 VueFlow 外部渲染） -->
    <div
      v-for="guide in screenGuides"
      :key="guide.key"
      :class="guide.class"
      :style="guide.style"
    />
  </section>
</template>

<style scoped lang="scss">
.workflow-canvas-viewport {
  position: absolute;
  inset: 0;
  background: #f7f8fa;
}

.workflow-canvas-viewport :deep(.vue-flow) {
  width: 100%;
  height: 100%;
}

.workflow-canvas-viewport :deep(.vue-flow__controls) {
  display: none;
}

.workflow-canvas-viewport :deep(.workflow-minimap) {
  right: 18px;
  bottom: 18px;
  width: 168px;
  height: 112px;
  overflow: hidden;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fff;
}

.workflow-canvas-viewport :deep(.vue-flow__minimap-mask) {
  fill: rgba(245, 245, 245, 0.62);
  stroke: #1677ff;
  stroke-width: 1;
}

/* 对齐辅助线 - 覆盖层 */
.align-guide-overlay {
  position: absolute;
  pointer-events: none;
  z-index: 15;
  transition: opacity 0.12s ease;
}

.align-guide-vertical {
  border-left: 1px dashed rgba(22, 119, 255, 0.45);
}

.align-guide-horizontal {
  border-top: 1px dashed rgba(22, 119, 255, 0.45);
}
</style>

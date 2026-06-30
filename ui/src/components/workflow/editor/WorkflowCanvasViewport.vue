<script setup lang="ts">
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import { VueFlow, useVueFlow, type Connection } from '@vue-flow/core'
import WorkflowGraphEdge from '@/components/workflow/edge/WorkflowGraphEdge.vue'
import WorkflowGraphNode from '@/components/workflow/node/WorkflowGraphNode.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode } from '@/types/workflow'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'

const nodes = defineModel<WorkflowFlowNode[]>('nodes', { required: true })
const edges = defineModel<WorkflowFlowEdge[]>('edges', { required: true })

const props = defineProps<{
  locked: boolean
}>()

const emit = defineEmits<{
  selectNode: [nodeId: string | null]
  nodeContext: [payload: { nodeId: string; x: number; y: number }]
  paneClick: []
  showLibrary: [payload: { sourceNodeId: string; sourceHandle: string; x: number; y: number }]
  showLibraryFromEdge: [payload: { edgeId: string; x: number; y: number }]
}>()

const flow = useVueFlow()

function getSourceHandles(nodeId: string) {
  const node = nodes.value.find((item) => item.id === nodeId)
  if (!node || node.data.type === 'END') return []
  const branchHandles = node.data.schema?.branchHandles || []
  return branchHandles.length ? branchHandles.map((item) => item.id) : ['output']
}

function onConnect(connection: Connection) {
  if (props.locked) return
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

function onNodeClick(event: any) {
  if (props.locked) return
  emit('selectNode', event.node.id)
}

function onNodeContextMenu(event: any) {
  if (props.locked) return
  event.event?.preventDefault()
  emit('nodeContext', { nodeId: event.node.id, x: event.event?.clientX || 0, y: event.event?.clientY || 0 })
}

function onPaneClick() {
  if (props.locked) return
  emit('selectNode', null)
  emit('paneClick')
}

function onNodeAddClick(nodeId: string, payload: { x: number; y: number; sourceHandle: string }) {
  if (props.locked) return
  emit('showLibrary', { sourceNodeId: nodeId, ...payload })
}

function onEdgeAddClick(payload: { edgeId: string; x: number; y: number }) {
  if (props.locked) return
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

defineExpose({ addAtCenter, fitAll, zoomInCanvas, zoomOutCanvas, resetZoom, fitNode })
</script>

<template>
  <section class="workflow-canvas-viewport">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      fit-view-on-init
      :nodes-draggable="!locked"
      :nodes-connectable="!locked"
      :edges-updatable="!locked"
      :default-edge-options="{ animated: false, type: 'workflow' }"
      @connect="onConnect"
      @node-click="onNodeClick"
      @node-context-menu="onNodeContextMenu"
      @pane-click="onPaneClick"
    >
      <template #node-workflow="slotProps">
        <WorkflowGraphNode
          v-bind="slotProps"
          :locked="locked"
          @add-node="(pos: any) => onNodeAddClick(String(slotProps.id), pos)"
        />
      </template>
      <template #edge-workflow="slotProps">
        <WorkflowGraphEdge v-bind="slotProps" :locked="locked" @add-node="onEdgeAddClick" />
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
</style>

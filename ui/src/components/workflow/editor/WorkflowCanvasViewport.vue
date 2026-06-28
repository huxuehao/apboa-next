<script setup lang="ts">
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import { VueFlow, useVueFlow, type Connection } from '@vue-flow/core'
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
  showLibrary: [payload: { sourceNodeId: string; x: number; y: number }]
}>()

const flow = useVueFlow()

function onConnect(connection: Connection) {
  if (props.locked) return
  flow.addEdges([
    {
      id: `edge-${connection.source}-${connection.target}-${Date.now()}`,
      ...connection,
      type: 'default',
    },
  ])
}

function onNodeClick(event: any) {
  emit('selectNode', event.node.id)
}

function onNodeContextMenu(event: any) {
  event.event?.preventDefault()
  emit('selectNode', event.node.id)
  emit('nodeContext', { nodeId: event.node.id, x: event.event?.clientX || 0, y: event.event?.clientY || 0 })
}

function onPaneClick() {
  emit('selectNode', null)
  emit('paneClick')
}

function onNodeAddClick(nodeId: string, position: { x: number; y: number }) {
  emit('showLibrary', { sourceNodeId: nodeId, ...position })
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
      :default-edge-options="{ animated: false, type: 'default' }"
      @connect="onConnect"
      @node-click="onNodeClick"
      @node-context-menu="onNodeContextMenu"
      @pane-click="onPaneClick"
    >
      <template #node-workflow="slotProps">
        <WorkflowGraphNode v-bind="slotProps" @add-node="(pos: any) => onNodeAddClick(String(slotProps.id), pos)" />
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

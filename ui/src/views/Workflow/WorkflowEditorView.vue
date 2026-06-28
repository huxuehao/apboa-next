<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import WorkflowCanvasViewport from '@/components/workflow/editor/WorkflowCanvasViewport.vue'
import WorkflowCanvasToolbar from '@/components/workflow/editor/WorkflowCanvasToolbar.vue'
import WorkflowTopActions from '@/components/workflow/editor/WorkflowTopActions.vue'
import WorkflowTopLeft from '@/components/workflow/editor/WorkflowTopLeft.vue'
import NodeLibraryPopover from '@/components/workflow/library/NodeLibraryPopover.vue'
import WorkflowConfigPanel from '@/components/workflow/panels/WorkflowConfigPanel.vue'
import WorkflowRunDock from '@/components/workflow/panels/WorkflowRunDock.vue'
import WorkflowNodeContextMenu from '@/components/workflow/context-menu/WorkflowNodeContextMenu.vue'
import { useWorkflowStore } from '@/stores'
import * as workflowApi from '@/api/workflow'
import {
  cloneDefaultConfig,
  cloneDefaultInputs,
  cloneDefaultOutputs,
  getWorkflowNodeSchema,
} from '@/config/workflow/nodeSchemas'
import type {
  Workflow,
  WorkflowDefinition,
  WorkflowFlowEdge,
  WorkflowFlowNode,
  WorkflowNodeDefinition,
  WorkflowNodeSchema,
  WorkflowResourceMaps,
  WorkflowRunRequest,
} from '@/types/workflow'

type CanvasRef = InstanceType<typeof WorkflowCanvasViewport>

const route = useRoute()
const router = useRouter()
const store = useWorkflowStore()

const workflow = ref<Workflow>({})
const nodes = ref<WorkflowFlowNode[]>([])
const edges = ref<WorkflowFlowEdge[]>([])
const selectedNodeId = ref<string | null>(null)
const saving = ref(false)
const running = ref(false)
const locked = ref(false)
const libraryOpen = ref(false)
const libraryAnchorX = ref<number | undefined>(undefined)
const libraryAnchorY = ref<number | undefined>(undefined)
const pendingSourceNodeId = ref<string | null>(null)
const runDockOpen = ref(false)
const runInput = ref('{\n  "body": {},\n  "variables": {}\n}')
const canvasRef = ref<CanvasRef | null>(null)
const resources = ref<WorkflowResourceMaps>({ caches: [], datasources: [], mqs: [] })
const history = ref<WorkflowDefinition[]>([])
const future = ref<WorkflowDefinition[]>([])
const contextMenu = ref({ open: false, nodeId: '', x: 0, y: 0 })

const workflowId = computed(() => String(route.params.id || ''))
const selectedNode = computed(() => nodes.value.find((item) => item.id === selectedNodeId.value) || null)
const canUndo = computed(() => history.value.length > 0)
const canRedo = computed(() => future.value.length > 0)

onMounted(async () => {
  await loadResources()
  if (workflowId.value) {
    await loadWorkflow(workflowId.value)
  } else {
    workflow.value = {
      name: '未命名工作流',
      status: 'DRAFT',
      version: '0',
      locked: 0,
      enabled: true,
      config: defaultDefinition(),
    }
    loadDefinition(workflow.value.config || defaultDefinition())
  }
})

async function loadResources() {
  const [caches, datasources, mqs] = await Promise.all([
    workflowApi.enabledCaches(),
    workflowApi.enabledDatasources(),
    workflowApi.enabledMqs(),
  ])
  resources.value = {
    caches: caches.data.data || [],
    datasources: datasources.data.data || [],
    mqs: mqs.data.data || [],
  }
}

async function loadWorkflow(id: string) {
  const response = await workflowApi.workflowDetail(id)
  workflow.value = response.data.data.workflow || {}
  locked.value = Boolean(workflow.value.locked)
  loadDefinition(workflow.value.config || defaultDefinition())
  await nextTick()
  canvasRef.value?.fitAll()
}

function loadDefinition(definition: WorkflowDefinition) {
  nodes.value = (definition.nodes || []).map(toFlowNode)
  edges.value = (definition.edges || []).map((edge) => ({ ...edge, type: 'default' }))
  history.value = []
  future.value = []
}

function restoreDefinition(definition: WorkflowDefinition) {
  nodes.value = (definition.nodes || []).map(toFlowNode)
  edges.value = (definition.edges || []).map((edge) => ({ ...edge, type: 'default' }))
}

function toFlowNode(node: WorkflowNodeDefinition): WorkflowFlowNode {
  const schema = getWorkflowNodeSchema(node.type)
  return {
    id: node.id,
    type: 'workflow',
    position: node.position,
    data: {
      type: node.type,
      label: node.name || schema?.title || node.type,
      description: schema?.description,
      status: 'IDLE',
      config: { ...(schema?.defaultConfig || {}), ...(node.config || {}) },
      inputConfigs: node.inputConfigs || schema?.inputConfigs || [],
      outputConfigs: node.outputConfigs || cloneDefaultOutputs(schema || getWorkflowNodeSchema('END')!, node.id),
      schema,
      resources: resources.value,
    },
  }
}

function toDefinition(): WorkflowDefinition {
  return {
    nodes: nodes.value.map((node) => ({
      id: node.id,
      type: node.data.type,
      name: node.data.label,
      position: node.position,
      config: node.data.config || {},
      inputConfigs: node.data.inputConfigs || [],
      outputConfigs: node.data.outputConfigs || [{ name: 'output', fromNodeId: node.id }],
      ui: {},
    })),
    edges: edges.value.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle || 'output',
      targetHandle: edge.targetHandle || 'input',
      label: String(edge.label || ''),
    })),
    viewport: { x: 0, y: 0, zoom: 1 },
    metadata: {
      schemaVersion: '1.0',
      nodeVersion: '1.0',
      updatedAt: new Date().toISOString(),
    },
  }
}

function defaultDefinition(): WorkflowDefinition {
  const start = createDefinitionNode('start', getWorkflowNodeSchema('START')!, { x: 120, y: 180 })
  const end = createDefinitionNode('end', getWorkflowNodeSchema('END')!, { x: 520, y: 180 })
  end.inputConfigs = [{ name: 'input', sourceType: 'NODE_OUTPUT', nodeId: 'start', outputName: 'output' }]
  return {
    nodes: [start, end],
    edges: [{ id: 'edge-start-end', source: 'start', target: 'end', sourceHandle: 'output', targetHandle: 'input', label: '' }],
    viewport: { x: 0, y: 0, zoom: 1 },
    metadata: { schemaVersion: '1.0', nodeVersion: '1.0', updatedAt: new Date().toISOString() },
  }
}

function createDefinitionNode(id: string, schema: WorkflowNodeSchema, position: { x: number; y: number }): WorkflowNodeDefinition {
  return {
    id,
    type: schema.type,
    name: schema.title,
    position,
    config: cloneDefaultConfig(schema),
    inputConfigs: cloneDefaultInputs(schema),
    outputConfigs: cloneDefaultOutputs(schema, id),
    ui: {},
  }
}

function snapshot() {
  history.value.push(toDefinition())
  if (history.value.length > 50) history.value.shift()
  future.value = []
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}

function addNode(schema: WorkflowNodeSchema) {
  if (locked.value) {
    message.warning('画布已锁定，无法添加节点')
    return
  }
  snapshot()
  const id = `${schema.type.toLowerCase()}-${Date.now()}`
  const sourceId = pendingSourceNodeId.value
  const sourceNode = sourceId ? nodes.value.find((n) => n.id === sourceId) : null
  const position = sourceNode
    ? { x: sourceNode.position.x + 320, y: sourceNode.position.y }
    : canvasRef.value?.addAtCenter() || { x: 240, y: 240 }
  nodes.value.push(toFlowNode(createDefinitionNode(id, schema, position)))
  if (sourceId) {
    edges.value.push({
      id: `edge-${sourceId}-${id}-${Date.now()}`,
      source: sourceId,
      target: id,
      sourceHandle: 'output',
      targetHandle: 'input',
      type: 'default',
    })
    pendingSourceNodeId.value = null
    libraryAnchorX.value = undefined
    libraryAnchorY.value = undefined
  }
  selectedNodeId.value = id
}

function updateNode(node: WorkflowFlowNode) {
  snapshot()
  nodes.value = nodes.value.map((item) => (item.id === node.id ? { ...node, data: { ...node.data, resources: resources.value } } : item))
}

function deleteNode(nodeId: string) {
  if (locked.value) {
    message.warning('画布已锁定，无法删除节点')
    return
  }
  snapshot()
  nodes.value = nodes.value.filter((node) => node.id !== nodeId)
  edges.value = edges.value.filter((edge) => edge.source !== nodeId && edge.target !== nodeId)
  if (selectedNodeId.value === nodeId) selectedNodeId.value = null
  closeContextMenu()
}

function copyNode(nodeId: string) {
  const source = nodes.value.find((node) => node.id === nodeId)
  if (!source || locked.value) return
  snapshot()
  const id = `${source.data.type.toLowerCase()}-${Date.now()}`
  nodes.value.push({
    ...source,
    id,
    position: { x: source.position.x + 40, y: source.position.y + 40 },
    data: {
      ...source.data,
      config: clone(source.data.config),
      inputConfigs: clone(source.data.inputConfigs || []),
      label: `${source.data.label} 副本`,
      outputConfigs: cloneDefaultOutputs(source.data.schema!, id),
      resources: resources.value,
    },
  })
  selectedNodeId.value = id
  closeContextMenu()
}

function autoLayout() {
  if (!nodes.value.length || locked.value) return
  snapshot()
  const indegree = new Map(nodes.value.map((node) => [node.id, 0]))
  edges.value.forEach((edge) => indegree.set(edge.target, (indegree.get(edge.target) || 0) + 1))
  const levels = new Map<string, number>()
  const queue = nodes.value.filter((node) => (indegree.get(node.id) || 0) === 0).map((node) => node.id)
  queue.forEach((id) => levels.set(id, 0))
  while (queue.length) {
    const id = queue.shift()!
    const level = levels.get(id) || 0
    edges.value.filter((edge) => edge.source === id).forEach((edge) => {
      const nextLevel = Math.max(levels.get(edge.target) || 0, level + 1)
      levels.set(edge.target, nextLevel)
      indegree.set(edge.target, (indegree.get(edge.target) || 0) - 1)
      if ((indegree.get(edge.target) || 0) === 0) queue.push(edge.target)
    })
  }
  const buckets = new Map<number, WorkflowFlowNode[]>()
  nodes.value.forEach((node) => {
    const level = levels.get(node.id) || 0
    buckets.set(level, [...(buckets.get(level) || []), node])
  })
  nodes.value = nodes.value.map((node) => {
    const level = levels.get(node.id) || 0
    const bucket = buckets.get(level) || []
    const index = bucket.findIndex((item) => item.id === node.id)
    return { ...node, position: { x: 120 + level * 320, y: 120 + index * 180 } }
  })
  nextTick(() => canvasRef.value?.fitAll())
}

function undo() {
  const previous = history.value.pop()
  if (!previous) return
  future.value.push(toDefinition())
  restoreDefinition(previous)
}

function redo() {
  const next = future.value.pop()
  if (!next) return
  history.value.push(toDefinition())
  restoreDefinition(next)
}

async function saveWorkflow() {
  if (!workflow.value.id && !workflowId.value) {
    const response = await workflowApi.workflowSave({
      ...workflow.value,
      config: toDefinition(),
      status: workflow.value.status || 'DRAFT',
      version: workflow.value.version || '0',
    })
    workflow.value = response.data.data
    message.success('工作流已创建')
    if (workflow.value.id) await router.replace(`/workflow/${workflow.value.id}/edit`)
    return
  }
  if (!workflow.value.id) return
  saving.value = true
  try {
    workflow.value.config = toDefinition()
    await workflowApi.workflowUpdate(workflow.value)
    message.success('已保存')
  } finally {
    saving.value = false
  }
}

async function validateWorkflow() {
  await saveWorkflow()
  if (!workflow.value.id) return false
  const result = await store.validate(workflow.value.id)
  markValidation(result.valid, result.errors)
  if (result.valid) {
    message.success('校验通过')
  } else {
    message.error('校验失败，请查看标红节点')
  }
  return result.valid
}

async function publishWorkflow() {
  const valid = await validateWorkflow()
  if (!valid || !workflow.value.id) return
  Modal.confirm({
    title: '发布工作流',
    content: '发布后会生成不可变版本，正式运行将使用最新发布版本。',
    okText: '发布',
    cancelText: '取消',
    onOk: async () => {
      const response = await workflowApi.workflowPublish(workflow.value.id!)
      workflow.value.status = 'PUBLISHED'
      workflow.value.version = response.data.data.version
      message.success('发布成功')
    },
  })
}

function openDebugPanel() {
  runDockOpen.value = true
}

async function debugRun() {
  if (!workflow.value.id) {
    await saveWorkflow()
  } else {
    await saveWorkflow()
  }
  if (!workflow.value.id) return
  running.value = true
  try {
    const payload = JSON.parse(runInput.value || '{}') as WorkflowRunRequest
    const result = await store.debugRun(workflow.value.id, payload)
    const statusByNode = new Map(result.nodeExecutions.map((item) => [item.nodeId, item.status]))
    nodes.value = nodes.value.map((node) => ({
      ...node,
      data: { ...node.data, status: statusByNode.get(node.id) || node.data.status },
    }))
    message.success(result.run.status === 'SUCCESS' ? '调试运行成功' : '调试运行结束，请查看结果')
  } catch (error) {
    message.error('调试输入不是合法 JSON，或运行失败')
    throw error
  } finally {
    running.value = false
  }
}

function markValidation(valid: boolean, errors: unknown[]) {
  const errorMap = new Map<string, string[]>()
  errors.forEach((item) => {
    if (typeof item === 'object' && item && 'nodeId' in item) {
      const nodeId = String((item as { nodeId?: string }).nodeId || '')
      const msg = String((item as { message?: string }).message || '配置错误')
      if (nodeId) errorMap.set(nodeId, [...(errorMap.get(nodeId) || []), msg])
    }
  })
  nodes.value = nodes.value.map((node) => ({
    ...node,
    data: {
      ...node.data,
      status: valid ? 'IDLE' : errorMap.has(node.id) ? 'INVALID' : node.data.status,
      errors: errorMap.get(node.id) || [],
    },
  }))
  const firstInvalid = nodes.value.find((node) => errorMap.has(node.id))
  if (firstInvalid) selectedNodeId.value = firstInvalid.id
}

function showVersions() {
  if (!workflow.value.id) return
  workflowApi.workflowVersions(workflow.value.id).then((response) => {
    const versions = response.data.data || []
    Modal.info({
      title: '版本记录',
      content: versions.length ? versions.map((item) => `v${item.version} · ${item.createdAt || ''}`).join('\n') : '暂无版本记录',
    })
  })
}

function showRuns() {
  message.info('运行记录入口已保留，后续可扩展为独立抽屉。')
}

function openContextMenu(payload: { nodeId: string; x: number; y: number }) {
  contextMenu.value = { open: true, ...payload }
}

function closeContextMenu() {
  contextMenu.value.open = false
}

function openLibraryFromNode(payload: { sourceNodeId: string; x: number; y: number }) {
  pendingSourceNodeId.value = payload.sourceNodeId
  libraryAnchorX.value = payload.x
  libraryAnchorY.value = payload.y
  libraryOpen.value = true
}

function focusNode(nodeId: string) {
  selectedNodeId.value = nodeId
  canvasRef.value?.fitNode(nodeId)
}
</script>

<template>
  <main class="workflow-editor-shell">
    <WorkflowCanvasViewport
      ref="canvasRef"
      v-model:nodes="nodes"
      v-model:edges="edges"
      :locked="locked"
      @select-node="selectedNodeId = $event"
      @node-context="openContextMenu"
      @pane-click="closeContextMenu"
      @show-library="openLibraryFromNode"
    />

    <WorkflowTopLeft
      :title="workflow.name || ''"
      :status="workflow.status"
      :version="workflow.version"
      :saving="saving"
      @back="router.push('/workflow')"
      @update-title="(value) => (workflow.name = value)"
    />

    <WorkflowTopActions
      :saving="saving"
      :running="running"
      @save="saveWorkflow"
      @validate="validateWorkflow"
      @publish="publishWorkflow"
      @debug="openDebugPanel"
      @versions="showVersions"
      @runs="showRuns"
    />

    <WorkflowCanvasToolbar
      :locked="locked"
      :can-undo="canUndo"
      :can-redo="canRedo"
      :has-nodes="nodes.length > 0"
      :library-open="libraryOpen"
      @add-node="libraryOpen = !libraryOpen"
      @fit="canvasRef?.fitAll()"
      @zoom-in="canvasRef?.zoomInCanvas()"
      @zoom-out="canvasRef?.zoomOutCanvas()"
      @reset-zoom="canvasRef?.resetZoom()"
      @toggle-lock="locked = !locked"
      @undo="undo"
      @redo="redo"
      @layout="autoLayout"
      @clear-selection="selectedNodeId = null"
    />

    <NodeLibraryPopover :open="libraryOpen" :anchor-x="libraryAnchorX" :anchor-y="libraryAnchorY" @close="libraryOpen = false; pendingSourceNodeId = null; libraryAnchorX = undefined; libraryAnchorY = undefined" @add="addNode" />

    <div v-if="libraryOpen" class="popover-mask" @click="libraryOpen = false; pendingSourceNodeId = null; libraryAnchorX = undefined; libraryAnchorY = undefined" />

    <WorkflowConfigPanel
      :node="selectedNode"
      :nodes="nodes"
      :resources="resources"
      @update="updateNode"
      @close="selectedNodeId = null"
    />

    <WorkflowRunDock
      v-model:input-text="runInput"
      :open="runDockOpen"
      :result="store.lastRun"
      :loading="running"
      @run="debugRun"
      @close="runDockOpen = false"
      @focus-node="focusNode"
    />

    <WorkflowNodeContextMenu
      :open="contextMenu.open"
      :x="contextMenu.x"
      :y="contextMenu.y"
      @edit="selectedNodeId = contextMenu.nodeId; closeContextMenu()"
      @rename="selectedNodeId = contextMenu.nodeId; closeContextMenu()"
      @copy="copyNode(contextMenu.nodeId)"
      @delete="deleteNode(contextMenu.nodeId)"
      @fit="focusNode(contextMenu.nodeId); closeContextMenu()"
      @logs="runDockOpen = true; closeContextMenu()"
    />
  </main>
</template>

<style scoped lang="scss">
.workflow-editor-shell {
  position: relative;
  width: 100%;
  height: calc(100vh - 55px);
  overflow: hidden;
  background: #f7f8fa;
}

.popover-mask {
  position: absolute;
  inset: 0;
  z-index: 1;
}
</style>

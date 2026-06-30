<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { Graph, layout as dagreLayout } from '@dagrejs/dagre'
import WorkflowCanvasViewport from '@/components/workflow/editor/WorkflowCanvasViewport.vue'
import WorkflowCanvasToolbar from '@/components/workflow/editor/WorkflowCanvasToolbar.vue'
import WorkflowTopActions from '@/components/workflow/editor/WorkflowTopActions.vue'
import WorkflowTopLeft from '@/components/workflow/editor/WorkflowTopLeft.vue'
import NodeLibraryPopover from '@/components/workflow/library/NodeLibraryPopover.vue'
import WorkflowConfigPanel from '@/components/workflow/panels/WorkflowConfigPanel.vue'
import WorkflowRunDock from '@/components/workflow/panels/WorkflowRunDock.vue'
import WorkflowValidationPanel from '@/components/workflow/panels/WorkflowValidationPanel.vue'
import WorkflowPublishModal from '@/components/workflow/version/WorkflowPublishModal.vue'
import WorkflowVersionModal from '@/components/workflow/version/WorkflowVersionModal.vue'
import WorkflowNodeContextMenu from '@/components/workflow/context-menu/WorkflowNodeContextMenu.vue'
import { useWorkflowStore } from '@/stores'
import * as workflowApi from '@/api/workflow'
import {
  cloneDefaultConfig,
  cloneDefaultInputs,
  cloneDefaultOutputs,
  getWorkflowNodeSchema,
} from '@/config/workflow/nodeSchemas'
import { createDefaultWorkflowDefinition, ensureWorkflowDefinition } from '@/utils/workflow/defaultDefinition'
import type {
  Workflow,
  WorkflowDefinition,
  WorkflowFlowEdge,
  WorkflowFlowNode,
  WorkflowNodeDefinition,
  WorkflowNodeSchema,
  WorkflowResourceMaps,
  WorkflowRunRequest,
  WorkflowValidationResult,
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
const lockToggling = ref(false)
const libraryOpen = ref(false)
const libraryAnchorX = ref<number | undefined>(undefined)
const libraryAnchorY = ref<number | undefined>(undefined)
const libraryAnchorTopOffset = ref(0)
const pendingSourceNodeId = ref<string | null>(null)
const pendingSourceHandle = ref<string>('output')
const pendingEdgeId = ref<string | null>(null)
const runDockOpen = ref(false)
const runDockWidth = ref(440)
const versionModalOpen = ref(false)
const validationPanelOpen = ref(false)
const validationPanelWidth = ref(440)
const validationResult = ref<WorkflowValidationResult | null>(null)
const publishModalOpen = ref(false)
const publishing = ref(false)
const runInput = ref('{\n  "params": [],\n  "variables": {}\n}')
const canvasRef = ref<CanvasRef | null>(null)
const resources = ref<WorkflowResourceMaps>({ caches: [], datasources: [], mqs: [] })
const history = ref<WorkflowDefinition[]>([])
const future = ref<WorkflowDefinition[]>([])
const contextMenu = ref({ open: false, nodeId: '', x: 0, y: 0 })

const workflowId = computed(() => String(route.params.id || ''))
const selectedNode = computed(() => nodes.value.find((item) => item.id === selectedNodeId.value) || null)
const activeSidePanelWidth = computed(() => {
  if (validationPanelOpen.value) return validationPanelWidth.value
  if (runDockOpen.value) return runDockWidth.value
  return 0
})
const configPanelRightOffset = computed(() => (activeSidePanelWidth.value ? activeSidePanelWidth.value + 28 : 16))
const canUndo = computed(() => history.value.length > 0)
const canRedo = computed(() => future.value.length > 0)
const nodeNames = computed(() =>
  nodes.value.reduce<Record<string, string>>((acc, node) => {
    acc[node.id] = node.data.label || node.id
    return acc
  }, {}),
)

onMounted(async () => {
  await loadResources()
  if (workflowId.value) {
    await loadWorkflow(workflowId.value)
  } else {
    const definition = createDefaultWorkflowDefinition()
    workflow.value = {
      name: '未命名工作流',
      status: 'DRAFT',
      version: '0',
      locked: 0,
      enabled: true,
      config: definition,
    }
    loadDefinition(definition)
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
  workflow.value.config = ensureWorkflowDefinition(workflow.value.config)
  loadDefinition(workflow.value.config)
  await nextTick()
  canvasRef.value?.fitAll()
}

async function refreshWorkflowDetail(reloadCanvas = false) {
  if (!workflow.value.id) return
  const response = await workflowApi.workflowDetail(workflow.value.id)
  workflow.value = response.data.data.workflow || workflow.value
  locked.value = Boolean(workflow.value.locked)
  if (reloadCanvas) {
    workflow.value.config = ensureWorkflowDefinition(workflow.value.config)
    loadDefinition(workflow.value.config)
    await nextTick()
    canvasRef.value?.fitAll()
  }
}

function loadDefinition(definition: WorkflowDefinition) {
  const safeDefinition = ensureWorkflowDefinition(definition)
  nodes.value = (safeDefinition.nodes || []).map(toFlowNode)
  edges.value = (safeDefinition.edges || []).map(toFlowEdge)
  history.value = []
  future.value = []
}

function restoreDefinition(definition: WorkflowDefinition) {
  const safeDefinition = ensureWorkflowDefinition(definition)
  nodes.value = (safeDefinition.nodes || []).map(toFlowNode)
  edges.value = (safeDefinition.edges || []).map(toFlowEdge)
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

function toFlowEdge(edge: WorkflowDefinition['edges'][number]): WorkflowFlowEdge {
  return { ...edge, type: 'workflow' }
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
  if (pendingEdgeId.value) {
    insertNodeOnEdge(schema, pendingEdgeId.value)
    return
  }
  snapshot()
  const id = `${schema.type.toLowerCase()}-${Date.now()}`
  const sourceId = pendingSourceNodeId.value
  const sourceHandle = pendingSourceHandle.value || 'output'
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
      sourceHandle,
      targetHandle: 'input',
      type: 'workflow',
    })
    pendingSourceNodeId.value = null
    pendingSourceHandle.value = 'output'
    pendingEdgeId.value = null
    libraryAnchorX.value = undefined
    libraryAnchorY.value = undefined
  }
  selectedNodeId.value = id
}

function getDefaultSourceHandle(schema: WorkflowNodeSchema) {
  return schema.branchHandles?.[0]?.id || 'output'
}

function collectDownstreamNodeIds(startNodeId: string) {
  const visited = new Set<string>()
  const queue = [startNodeId]
  while (queue.length) {
    const nodeId = queue.shift()!
    if (visited.has(nodeId)) continue
    visited.add(nodeId)
    edges.value
      .filter((edge) => edge.source === nodeId)
      .forEach((edge) => {
        if (!visited.has(edge.target)) queue.push(edge.target)
      })
  }
  return visited
}

function insertNodeOnEdge(schema: WorkflowNodeSchema, edgeId: string) {
  if (schema.type === 'START' || schema.type === 'END') {
    message.warning('开始和结束节点不能插入到连线中')
    return
  }
  const originalEdge = edges.value.find((edge) => edge.id === edgeId)
  if (!originalEdge) {
    message.warning('连线已变化，请重新选择插入位置')
    clearPendingAdd()
    return
  }
  const sourceNode = nodes.value.find((node) => node.id === originalEdge.source)
  const targetNode = nodes.value.find((node) => node.id === originalEdge.target)
  if (!sourceNode || !targetNode) {
    message.warning('连线节点不存在，请重新选择插入位置')
    clearPendingAdd()
    return
  }
  snapshot()
  const id = `${schema.type.toLowerCase()}-${Date.now()}`
  const shiftX = 320
  const downstreamIds = collectDownstreamNodeIds(originalEdge.target)
  const position = {
    x: sourceNode.position.x + shiftX,
    y: targetNode.position.y,
  }
  nodes.value = nodes.value.map((node) =>
    downstreamIds.has(node.id)
      ? { ...node, position: { x: node.position.x + shiftX, y: node.position.y } }
      : node,
  )
  nodes.value.push(toFlowNode(createDefinitionNode(id, schema, position)))
  edges.value = edges.value.filter((edge) => edge.id !== originalEdge.id)
  edges.value.push(
    {
      id: `edge-${originalEdge.source}-${id}-${Date.now()}`,
      source: originalEdge.source,
      target: id,
      sourceHandle: originalEdge.sourceHandle || 'output',
      targetHandle: 'input',
      type: 'workflow',
    },
    {
      id: `edge-${id}-${originalEdge.target}-${Date.now()}`,
      source: id,
      target: originalEdge.target,
      sourceHandle: getDefaultSourceHandle(schema),
      targetHandle: originalEdge.targetHandle || 'input',
      type: 'workflow',
    },
  )
  selectedNodeId.value = id
  clearPendingAdd()
}

function updateNode(node: WorkflowFlowNode) {
  if (locked.value) {
    message.warning('工作流已锁定，解锁后再编辑节点配置')
    return
  }
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

function getLayoutEdgeWeight(edge: WorkflowFlowEdge) {
  const sourceNode = nodes.value.find((node) => node.id === edge.source)
  const branchHandles = sourceNode?.data.schema?.branchHandles || []
  return branchHandles.length && edge.sourceHandle ? 1 : 2
}

function autoLayout() {
  if (!nodes.value.length || locked.value) return
  snapshot()
  const graph = new Graph({ multigraph: true, compound: false })
  graph.setGraph({
    rankdir: 'LR',
    align: 'UL',
    ranker: 'network-simplex',
    nodesep: 96,
    ranksep: 160,
    edgesep: 56,
    marginx: 120,
    marginy: 100,
  })
  graph.setDefaultEdgeLabel(() => ({}))
  nodes.value.forEach((node) => {
    graph.setNode(node.id, {
      width: 236,
      height: 124,
      rank: node.data.type === 'START' ? 'min' : node.data.type === 'END' ? 'max' : undefined,
    })
  })
  edges.value.forEach((edge) => {
    graph.setEdge(edge.source, edge.target, {
      id: edge.id,
      weight: getLayoutEdgeWeight(edge),
      minlen: 1,
      labeloffset: 16,
    }, edge.id)
  })
  dagreLayout(graph)
  nodes.value = nodes.value.map((node) => {
    const layoutNode = graph.node(node.id) as { x?: number; y?: number; width?: number; height?: number } | undefined
    if (!layoutNode?.x || !layoutNode?.y) return node
    return {
      ...node,
      position: {
        x: Math.round(layoutNode.x - (layoutNode.width || 236) / 2),
        y: Math.round(layoutNode.y - (layoutNode.height || 124) / 2),
      },
    }
  })
  nextTick(() => canvasRef.value?.fitAll())
}

function undo() {
  if (locked.value) return
  const previous = history.value.pop()
  if (!previous) return
  future.value.push(toDefinition())
  restoreDefinition(previous)
}

function redo() {
  if (locked.value) return
  const next = future.value.pop()
  if (!next) return
  history.value.push(toDefinition())
  restoreDefinition(next)
}

async function saveWorkflow(options: { allowLockedSkip?: boolean } = {}) {
  if (locked.value && workflow.value.id) {
    if (!options.allowLockedSkip) {
      message.warning('工作流已锁定，解锁后才能保存修改')
    }
    return Boolean(options.allowLockedSkip)
  }
  if (!workflow.value.id && !workflowId.value) {
    const response = await workflowApi.workflowSave({
      ...workflow.value,
      config: toDefinition(),
      status: workflow.value.status || 'DRAFT',
      version: workflow.value.version || '0',
    })
    workflow.value = response.data.data
    store.upsertWorkflow(workflow.value)
    store.markListDirty()
    message.success('工作流已创建')
    if (workflow.value.id) await router.replace(`/workflow/${workflow.value.id}/edit`)
    return true
  }
  if (!workflow.value.id) return false
  saving.value = true
  try {
    workflow.value.config = toDefinition()
    await workflowApi.workflowUpdate(workflow.value)
    await refreshWorkflowDetail(false)
    store.upsertWorkflow(workflow.value)
    store.markListDirty()
    message.success('已保存')
    return true
  } finally {
    saving.value = false
  }
}

async function validateWorkflow() {
  const canContinue = await saveWorkflow({ allowLockedSkip: true })
  if (!canContinue) return false
  if (!workflow.value.id) return false
  const result = await store.validate(workflow.value.id)
  validationResult.value = result
  validationPanelOpen.value = true
  runDockOpen.value = false
  selectedNodeId.value = null
  markValidation(result.valid, result.errors)
  if (result.valid) {
    message.success(result.warnings?.length ? '校验通过，请关注提醒项' : '校验通过')
  } else {
    message.error('校验失败，请查看校验结果')
  }
  return result.valid
}

async function publishWorkflow() {
  const valid = await validateWorkflow()
  if (!valid || !workflow.value.id) return
  publishModalOpen.value = true
}

async function submitPublish(remark?: string) {
  if (!workflow.value.id) return
  publishing.value = true
  try {
    const response = await workflowApi.workflowPublish(workflow.value.id, remark)
    workflow.value.status = 'PUBLISHED'
    workflow.value.version = response.data.data.version
    await refreshWorkflowDetail(false)
    store.upsertWorkflow(workflow.value)
    store.markListDirty()
    publishModalOpen.value = false
    message.success('发布成功')
  } finally {
    publishing.value = false
  }
}

function openDebugPanel() {
  runDockOpen.value = true
  validationPanelOpen.value = false
}

async function debugRun() {
  const canContinue = await saveWorkflow({ allowLockedSkip: true })
  if (!canContinue) return
  if (!workflow.value.id) return
  running.value = true
  try {
    let payload: WorkflowRunRequest
    try {
      payload = JSON.parse(runInput.value || '{}') as WorkflowRunRequest
    } catch {
      message.error('调试输入必须是合法 JSON')
      return
    }
    const inputError = validateDebugPayload(payload)
    if (inputError) {
      message.error(inputError)
      return
    }
    const result = await store.debugRun(workflow.value.id, payload)
    const statusByNode = new Map(result.nodeExecutions.map((item) => [item.nodeId, item.status]))
    nodes.value = nodes.value.map((node) => ({
      ...node,
      data: { ...node.data, status: statusByNode.get(node.id) || node.data.status },
    }))
    message.success(result.run.status === 'SUCCESS' ? '调试运行成功' : '调试运行结束，请查看结果')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '调试运行失败')
  } finally {
    running.value = false
  }
}

function validateDebugPayload(payload: WorkflowRunRequest) {
  const params = Array.isArray(payload.params) ? payload.params : []
  const values = new Map(params.map((item) => [item.name, item.value]))
  const start = nodes.value.find((node) => node.data.type === 'START')
  const startParams = Array.isArray(start?.data.config?.params) ? start.data.config.params as Array<Record<string, unknown>> : []
  for (const param of startParams) {
    const name = String(param.name || '')
    if (!name) continue
    const value = values.get(name)
    const type = String(param.type || 'String')
    if (param.required && (value === undefined || value === null || String(value).trim() === '')) {
      return `${name} 为必填参数`
    }
    if (value === undefined || value === null || String(value).trim() === '') continue
    if (type === 'Object' || type === 'Array') {
      try {
        const parsed = typeof value === 'string' ? JSON.parse(value) : value
        if (type === 'Array' && !Array.isArray(parsed)) return `${name} 必须是 JSON 数组`
        if (type === 'Object' && (Array.isArray(parsed) || typeof parsed !== 'object' || parsed === null)) return `${name} 必须是 JSON 对象`
      } catch {
        return `${name} 不是合法 JSON`
      }
    }
  }
  if (payload.variables && (Array.isArray(payload.variables) || typeof payload.variables !== 'object')) {
    return 'variables 必须是 JSON 对象'
  }
  return ''
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
}

function showVersions() {
  if (!workflow.value.id) return
  versionModalOpen.value = true
}

async function handleVersionLoaded(nextWorkflow: Workflow) {
  workflow.value = nextWorkflow
  locked.value = Boolean(workflow.value.locked)
  workflow.value.config = ensureWorkflowDefinition(workflow.value.config)
  loadDefinition(workflow.value.config)
  await nextTick()
  canvasRef.value?.fitAll()
  store.upsertWorkflow(workflow.value)
  store.markListDirty()
}

function openContextMenu(payload: { nodeId: string; x: number; y: number }) {
  contextMenu.value = { open: true, ...payload }
}

function closeContextMenu() {
  contextMenu.value.open = false
}

function clearPendingAdd() {
  pendingSourceNodeId.value = null
  pendingSourceHandle.value = 'output'
  pendingEdgeId.value = null
  libraryAnchorX.value = undefined
  libraryAnchorY.value = undefined
  libraryAnchorTopOffset.value = 0
}

function closeLibrary() {
  libraryOpen.value = false
  clearPendingAdd()
}

function toggleLibrary() {
  if (locked.value) return
  if (libraryOpen.value) {
    closeLibrary()
    return
  }
  clearPendingAdd()
  libraryOpen.value = true
}

function openLibraryFromNode(payload: { sourceNodeId: string; sourceHandle: string; x: number; y: number }) {
  if (locked.value) return
  pendingSourceNodeId.value = payload.sourceNodeId
  pendingSourceHandle.value = payload.sourceHandle || 'output'
  pendingEdgeId.value = null
  libraryAnchorX.value = payload.x
  libraryAnchorY.value = payload.y
  libraryAnchorTopOffset.value = 60
  selectedNodeId.value = null
  libraryOpen.value = true
}

function openLibraryFromEdge(payload: { edgeId: string; x: number; y: number }) {
  if (locked.value) return
  pendingSourceNodeId.value = null
  pendingSourceHandle.value = 'output'
  pendingEdgeId.value = payload.edgeId
  libraryAnchorX.value = payload.x
  libraryAnchorY.value = payload.y
  libraryAnchorTopOffset.value = 60
  selectedNodeId.value = null
  libraryOpen.value = true
}

function focusNode(nodeId: string) {
  selectedNodeId.value = nodeId
  canvasRef.value?.fitNode(nodeId)
}

function updateWorkflowTitle(value: string) {
  if (locked.value) return
  workflow.value.name = value
}

async function goBack() {
  store.markListDirty()
  await router.push('/workflow')
}

async function toggleWorkflowLock() {
  const nextLocked = locked.value ? 0 : 1
  if (!workflow.value.id) {
    locked.value = Boolean(nextLocked)
    workflow.value.locked = nextLocked
    return
  }
  lockToggling.value = true
  try {
    await workflowApi.workflowLock(workflow.value.id, nextLocked)
    locked.value = Boolean(nextLocked)
    workflow.value.locked = nextLocked
    store.upsertWorkflow({ ...workflow.value })
    store.markListDirty()
    if (locked.value) {
      selectedNodeId.value = null
      closeLibrary()
    }
    clearAllPanels()
    message.success(locked.value ? '已锁定编辑' : '已解除锁定')
  } finally {
    lockToggling.value = false
  }
}

function clearAllPanels() {
  selectedNodeId.value = null
  validationPanelOpen.value = false
  runDockOpen.value = false
  closeLibrary()
  closeContextMenu()
  publishModalOpen.value = false
  versionModalOpen.value = false
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
      @show-library-from-edge="openLibraryFromEdge"
    />

    <WorkflowTopLeft
      :title="workflow.name || ''"
      :status="workflow.status"
      :version="workflow.version"
      :saving="saving"
      :locked="locked"
      @back="goBack"
      @update-title="updateWorkflowTitle"
    />

    <WorkflowTopActions
      :saving="saving"
      :running="running"
      :locked="locked"
      @save="saveWorkflow"
      @validate="validateWorkflow"
      @publish="publishWorkflow"
      @debug="openDebugPanel"
      @versions="showVersions"
    />

    <WorkflowCanvasToolbar
      :locked="locked"
      :can-undo="canUndo"
      :can-redo="canRedo"
      :has-nodes="nodes.length > 0"
      :library-open="libraryOpen"
      :lock-toggling="lockToggling"
      @add-node="toggleLibrary"
      @fit="canvasRef?.fitAll()"
      @zoom-in="canvasRef?.zoomInCanvas()"
      @zoom-out="canvasRef?.zoomOutCanvas()"
      @reset-zoom="canvasRef?.resetZoom()"
      @toggle-lock="toggleWorkflowLock"
      @undo="undo"
      @redo="redo"
      @layout="autoLayout"
      @clear-selection="clearAllPanels"
    />

    <NodeLibraryPopover
      :open="libraryOpen"
      :anchor-x="libraryAnchorX"
      :anchor-y="libraryAnchorY"
      :anchor-top-offset="libraryAnchorTopOffset"
      @close="closeLibrary"
      @add="addNode"
    />

    <div v-if="libraryOpen" class="popover-mask" @click="closeLibrary" />

    <WorkflowConfigPanel
      :node="selectedNode"
      :nodes="nodes"
      :resources="resources"
      :right-offset="configPanelRightOffset"
      @update="updateNode"
      @close="selectedNodeId = null"
    />

    <WorkflowValidationPanel
      v-model:width="validationPanelWidth"
      :open="validationPanelOpen"
      :result="validationResult"
      :node-names="nodeNames"
      @close="validationPanelOpen = false"
      @focus-node="focusNode"
    />

    <WorkflowRunDock
      v-model:input-text="runInput"
      v-model:width="runDockWidth"
      :open="runDockOpen"
      :result="store.lastRun"
      :nodes="nodes"
      :loading="running"
      @run="debugRun"
      @close="runDockOpen = false"
      @focus-node="focusNode"
    />

    <WorkflowVersionModal
      v-model:open="versionModalOpen"
      :workflow-id="workflow.id"
      :workflow-name="workflow.name"
      :current-version="workflow.version"
      :status="workflow.status"
      @loaded="handleVersionLoaded"
    />

    <WorkflowPublishModal
      v-model:open="publishModalOpen"
      :workflow-name="workflow.name"
      :loading="publishing"
      @publish="submitPublish"
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
      @logs="openDebugPanel(); closeContextMenu()"
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

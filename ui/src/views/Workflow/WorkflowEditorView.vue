<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, provide } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
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
  WorkflowVariable,
} from '@/types/workflow'
import { useAccountStore } from '@/stores'
import { getToken } from '@/utils/auth'
import setting from '@/config/setting'

type CanvasRef = InstanceType<typeof WorkflowCanvasViewport>

const accountStore = useAccountStore()
const route = useRoute()
const router = useRouter()
const store = useWorkflowStore()

const workflow = ref<Workflow>({})
const nodes = ref<WorkflowFlowNode[]>([])
const edges = ref<WorkflowFlowEdge[]>([])
const selectedNodeId = ref<string | null>(null)
const saving = ref(false)
const saveState = ref<'idle' | 'dirty' | 'saved'>('idle')
const running = ref(false)
const lockToggling = ref(false)
const lockAcquired = ref(false)
const releasingLock = ref(false)
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
const validating = ref(false)
const validationResult = ref<WorkflowValidationResult | null>(null)
const publishModalOpen = ref(false)
const publishing = ref(false)
const leaveConfirmOpen = ref(false)
const leavingAfterSave = ref(false)
const runInput = ref('{\n  "params": [],\n  "variables": {}\n}')
const canvasRef = ref<CanvasRef | null>(null)
const resources = ref<WorkflowResourceMaps>({ caches: [], datasources: [], mqs: [], channels: [] })
const history = ref<WorkflowDefinition[]>([])
const future = ref<WorkflowDefinition[]>([])
const contextMenu = ref({ open: false, nodeId: '', x: 0, y: 0 })
const customVariables = ref<WorkflowVariable[]>([])

// 子流程编辑模式
const subWorkflowActive = ref(false)
const subWorkflowParentNodeId = ref<string | null>(null)
const subWorkflowParentLabel = ref('')
/** 进入子流程前父工作流的完整快照 */
const mainDefinitionSnapshot = ref<WorkflowDefinition | null>(null)
/** 主流程中 Loop 节点上游的所有节点（供子流程节点输出绑定使用） */
const parentUpstreamNodes = ref<WorkflowFlowNode[]>([])
/** 进入子流程前主流程的面板打开状态（退出时恢复） */
const savedPanelState = ref({ selectedNodeId: null as string | null, validationOpen: false, runDockOpen: false })
/** 进入子流程前父工作流画布位置（退出时恢复） */
const savedViewport = ref<{ x: number; y: number; zoom: number } | null>(null)

provide('subWorkflow', {
  active: subWorkflowActive,
  parentNodeId: subWorkflowParentNodeId,
  enter: enterSubWorkflow,
  exit: exitSubWorkflow,
})
provide('parentUpstreamNodes', parentUpstreamNodes)
provide('workflowVariables', customVariables)
provide('subWorkflowActive', subWorkflowActive)

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
const draftSignature = computed(() =>
  JSON.stringify({
    name: workflow.value.name || '',
    remark: workflow.value.remark || '',
    nodes: nodes.value.map((node) => ({
      id: node.id,
      type: node.data.type,
      name: node.data.label,
      position: node.position,
      config: node.data.config || {},
      inputConfigs: node.data.inputConfigs || [],
      outputConfigs: node.data.outputConfigs || [],
    })),
    edges: edges.value.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle || 'output',
      targetHandle: edge.targetHandle || 'input',
      label: String(edge.label || ''),
    })),
  }),
)
const savedDraftSignature = ref('')

const currentUserId = computed(() => String(accountStore.userInfo?.id || ''))
const lockOwnerId = computed(() => String(workflow.value.updatedBy || ''))
const lockOwnedByMe = computed(() =>
  Boolean(workflow.value.locked && currentUserId.value && lockOwnerId.value === currentUserId.value),
)
const readonly = computed(() => {
  if (accountStore.isReadOnly) return true
  // if (!workflow.value.id) return false
  return !lockAcquired.value || !lockOwnedByMe.value
})
const leaveConfirmMessage = computed(() =>
  saving.value
    ? '当前工作流正在保存中，直接退出可能无法确认保存结果。'
    : '当前工作流有未保存的修改，退出前可以先保存。',
)

onMounted(async () => {
  await loadResources()
  if (workflowId.value) {
    await loadWorkflow(workflowId.value)
    await acquireWorkflowLock()
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
    captureSavedDraft('idle')
  }
  window.addEventListener('pagehide', releaseWorkflowLockOnPageExit)
  window.addEventListener('beforeunload', releaseWorkflowLockOnPageExit)
})

onBeforeUnmount(() => {
  window.removeEventListener('pagehide', releaseWorkflowLockOnPageExit)
  window.removeEventListener('beforeunload', releaseWorkflowLockOnPageExit)
  releaseWorkflowLock()
})

onBeforeRouteLeave(async () => {
  await releaseWorkflowLock()
})

watch(draftSignature, (signature) => {
  if (!savedDraftSignature.value || saving.value || readonly.value || subWorkflowActive.value) return
  if (signature !== savedDraftSignature.value) {
    saveState.value = 'dirty'
  } else if (saveState.value === 'dirty') {
    saveState.value = 'idle'
  }
})

async function loadResources() {
  const [caches, datasources, mqs, channels] = await Promise.all([
    workflowApi.enabledCaches(),
    workflowApi.enabledDatasources(),
    workflowApi.enabledMqs(),
    workflowApi.enabledChannels(),
  ])
  resources.value = {
    caches: caches.data.data || [],
    datasources: datasources.data.data || [],
    mqs: mqs.data.data || [],
    channels: channels.data.data || [],
  }
}

function captureSavedDraft(state: 'idle' | 'saved' = 'saved') {
  savedDraftSignature.value = draftSignature.value
  saveState.value = state
}

async function acquireWorkflowLock(resetDraftState = true) {
  if (!workflow.value.id || accountStore.isReadOnly) {
    lockAcquired.value = false
    if (resetDraftState) captureSavedDraft('idle')
    return
  }
  lockToggling.value = true
  try {
    const response = await workflowApi.workflowLock(workflow.value.id, 1)
    if (!response.data.data) {
      lockAcquired.value = false
      await refreshWorkflowDetail(false)
      message.warning('当前工作流正在被其他用户编辑，已进入只读模式')
      return
    }
    await refreshWorkflowDetail(false)
    lockAcquired.value = lockOwnedByMe.value
    if (!lockAcquired.value) {
      message.warning('未能持有当前工作流编辑锁，已进入只读模式')
    }
  } finally {
    lockToggling.value = false
    if (resetDraftState) captureSavedDraft('idle')
  }
}

async function releaseWorkflowLock() {
  if (releasingLock.value || !workflow.value.id || !lockAcquired.value || !lockOwnedByMe.value) return
  releasingLock.value = true
  try {
    await workflowApi.workflowLock(workflow.value.id, 0)
    workflow.value.locked = 0
    lockAcquired.value = false
    store.markListDirty()
  } catch {
    // Browser lifecycle events may interrupt requests; manual unlock remains available as compensation.
  } finally {
    releasingLock.value = false
  }
}

function releaseWorkflowLockOnPageExit() {
  if (!workflow.value.id || !lockAcquired.value || !lockOwnedByMe.value) return
  lockAcquired.value = false
  const token = getToken()
  const headers: Record<string, string> = {}
  if (token) headers[setting.tokenHeader] = `Bearer ${token}`
  fetch(`${import.meta.env.VITE_APP_BASE_API || ''}/api/workflow/${workflow.value.id}/lock/0`, {
    method: 'PUT',
    headers,
    keepalive: true,
  }).catch(() => {})
}

async function loadWorkflow(id: string) {
  const response = await workflowApi.workflowDetail(id)
  workflow.value = response.data.data.workflow || {}
  workflow.value.config = ensureWorkflowDefinition(workflow.value.config)
  loadDefinition(workflow.value.config)
  lockAcquired.value = lockOwnedByMe.value
  captureSavedDraft('idle')
  await nextTick()
  canvasRef.value?.fitAll()
}

async function refreshWorkflowDetail(reloadCanvas = false) {
  if (!workflow.value.id) return
  const response = await workflowApi.workflowDetail(workflow.value.id)
  workflow.value = response.data.data.workflow || workflow.value
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
  customVariables.value = (safeDefinition.variables || []).filter((v) => v.source === 'custom')
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
    variables: customVariables.value,
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
  if (readonly.value) {
    message.warning('当前工作流为只读模式，无法添加节点')
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
  if (readonly.value) {
    message.warning('当前工作流为只读模式，无法编辑节点配置')
    return
  }
  snapshot()
  nodes.value = nodes.value.map((item) => (item.id === node.id ? { ...node, data: { ...node.data, resources: resources.value } } : item))
}

function deleteNode(nodeId: string) {
  deleteSelectedNodes([nodeId])
}

/** 统一删除方法：只读模式下忽略，snapshot 统一记录一次 */
function deleteSelectedNodes(nodeIds: string[]) {
  if (readonly.value) {
    message.warning('当前工作流为只读模式，无法删除节点')
    return
  }
  if (!nodeIds.length) return
  snapshot()
  const ids = new Set(nodeIds)
  nodes.value = nodes.value.filter((node) => !ids.has(node.id))
  edges.value = edges.value.filter((edge) => !ids.has(edge.source) && !ids.has(edge.target))
  if (selectedNodeId.value && ids.has(selectedNodeId.value)) selectedNodeId.value = null
  closeContextMenu()
}

function copyNode(nodeId: string) {
  const source = nodes.value.find((node) => node.id === nodeId)
  if (!source || readonly.value) return
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
  if (!nodes.value.length || readonly.value) return
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
  if (readonly.value) {
    message.warning('当前工作流为只读模式，无法保存修改')
    return false
  }
  saving.value = true
  if (!workflow.value.id && !workflowId.value) {
    try {
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
      captureSavedDraft('saved')
      if (workflow.value.id) {
        await acquireWorkflowLock(false)
        await router.replace(`/workflow/${workflow.value.id}`)
      }
      return true
    } finally {
      saving.value = false
    }
  }
  if (!workflow.value.id) {
    saving.value = false
    return false
  }
  if (saveState.value !== 'dirty') {
    saving.value = false
    return true
  }
  try {
    workflow.value.config = toDefinition()
    await workflowApi.workflowUpdate(workflow.value)
    await refreshWorkflowDetail(false)
    store.upsertWorkflow(workflow.value)
    store.markListDirty()
    captureSavedDraft('saved')
    return true
  } finally {
    saving.value = false
  }
}

async function validateWorkflow() {
  const canContinue = await saveWorkflow()
  if (!canContinue) return false
  if (!workflow.value.id) return false
  validationPanelOpen.value = true
  validationResult.value = null
  validating.value = true
  runDockOpen.value = false
  selectedNodeId.value = null
  try {
    const result = await store.validate(workflow.value.id)
    validationResult.value = result
    markValidation(result.valid, result.errors)
    return result.valid
  } finally {
    validating.value = false
  }
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
  const canContinue = await saveWorkflow()
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
    console.error('调试运行失败', error)
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
  workflow.value.config = ensureWorkflowDefinition(workflow.value.config)
  loadDefinition(workflow.value.config)
  captureSavedDraft('idle')
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
  if (readonly.value) return
  if (libraryOpen.value) {
    closeLibrary()
    return
  }
  clearPendingAdd()
  libraryOpen.value = true
}

function openLibraryFromNode(payload: { sourceNodeId: string; sourceHandle: string; x: number; y: number }) {
  if (readonly.value) return
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
  if (readonly.value) return
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
  if (readonly.value) return
  workflow.value.name = value
}

async function leaveEditor() {
  store.markListDirty()
  await router.push('/workflow')
}

function waitForSavingFinished() {
  if (!saving.value) return Promise.resolve()
  return new Promise<void>((resolve) => {
    const stop = watch(saving, (value) => {
      if (!value) {
        stop()
        resolve()
      }
    })
  })
}

function goBack() {
  if (subWorkflowActive.value) {
    exitSubWorkflow()
    return
  }
  if (saving.value || saveState.value === 'dirty') {
    leaveConfirmOpen.value = true
    return
  }
  void leaveEditor()
}

function leaveDirectly() {
  leaveConfirmOpen.value = false
  void leaveEditor()
}

async function saveAndLeave() {
  if (leavingAfterSave.value) return
  leavingAfterSave.value = true
  try {
    if (saving.value) await waitForSavingFinished()
    const saved = saveState.value === 'dirty' ? await saveWorkflow() : true
    if (!saved) return
    leaveConfirmOpen.value = false
    await leaveEditor()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    leavingAfterSave.value = false
  }
}

async function toggleWorkflowLock() {
  if (!workflow.value.id || accountStore.isReadOnly) return
  if (lockOwnedByMe.value) {
    await releaseWorkflowLock()
    await refreshWorkflowDetail(false)
    clearAllPanels()
    message.success('已解除锁定')
    return
  }
  await acquireWorkflowLock()
  if (lockAcquired.value) {
    message.success('已锁定编辑')
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

/** 清空画布上所有节点和连线 */
function clearAllNodes() {
  if (!nodes.value.length) return
  snapshot()
  nodes.value = []
  edges.value = []
  clearAllPanels()
}

/** 进入子流程编辑模式：保存父工作流快照，将 Loop 节点的 subNodes/subEdges 加载到画布 */
async function enterSubWorkflow(nodeId: string) {
  const loopNode = nodes.value.find((n) => n.id === nodeId)
  if (!loopNode || loopNode.data.type !== 'LOOP') return

  // 保存父工作流完整快照（用于退出时恢复）
  mainDefinitionSnapshot.value = toDefinition()
  history.value = []
  future.value = []
  subWorkflowParentNodeId.value = nodeId
  subWorkflowParentLabel.value = loopNode.data.label || '循环'
  subWorkflowActive.value = true
  savedPanelState.value = {
    selectedNodeId: selectedNodeId.value,
    validationOpen: validationPanelOpen.value,
    runDockOpen: runDockOpen.value,
  }
  savedViewport.value = canvasRef.value?.getViewport() || null
  selectedNodeId.value = null
  validationPanelOpen.value = false
  runDockOpen.value = false

  // 计算主流程中 Loop 节点上游的所有节点（供子流程节点输出绑定候选）
  parentUpstreamNodes.value = computeParentUpstreamNodes(nodeId)

  // 替换画布内容为子工作流
  const config = loopNode.data.config as Record<string, unknown>
  const subNodes = (config.subNodes as Array<Record<string, unknown>>) || []
  const subEdges = (config.subEdges as Array<Record<string, unknown>>) || []

  nodes.value = subNodes.map((sn) => toFlowNode(sn as unknown as WorkflowNodeDefinition)) as WorkflowFlowNode[]
  edges.value = (subEdges.map((se) => ({
    id: String(se.id || ''),
    source: String(se.source || ''),
    target: String(se.target || ''),
    sourceHandle: String(se.sourceHandle || 'output'),
    targetHandle: String(se.targetHandle || 'input'),
    type: 'workflow',
  })) as unknown) as WorkflowFlowEdge[]

  await nextTick()
  canvasRef.value?.resetZoom()
  await new Promise(r => setTimeout(r, 100))
  canvasRef.value?.fitAll()
}

/** 退出子流程编辑模式：回写子流程到 Loop 节点，恢复父工作流 */
async function exitSubWorkflow() {
  if (!subWorkflowActive.value || !subWorkflowParentNodeId.value || !mainDefinitionSnapshot.value) return

  // 1. 将当前画布的子流程序列化
  const subDef = toDefinition()

  // 2. 在父工作流快照中找到 Loop 节点并更新其 config
  const updatedNodes = mainDefinitionSnapshot.value.nodes.map((n) => {
    if (n.id === subWorkflowParentNodeId.value) {
      return {
        ...n,
        config: {
          ...(n.config || {}),
          subNodes: subDef.nodes,
          subEdges: subDef.edges,
        },
      }
    }
    return n
  })
  mainDefinitionSnapshot.value = { ...mainDefinitionSnapshot.value, nodes: updatedNodes }

  // 3. 恢复父工作流
  loadDefinition(mainDefinitionSnapshot.value)
  mainDefinitionSnapshot.value = null

  subWorkflowActive.value = false
  subWorkflowParentNodeId.value = null
  subWorkflowParentLabel.value = ''
  selectedNodeId.value = savedPanelState.value.selectedNodeId
  validationPanelOpen.value = savedPanelState.value.validationOpen
  runDockOpen.value = savedPanelState.value.runDockOpen
  parentUpstreamNodes.value = []

  await nextTick()
  if (savedViewport.value) {
    canvasRef.value?.restoreViewport(savedViewport.value)
  } else {
    canvasRef.value?.fitAll()
  }
}

/** 从当前 nodes/edges 计算指定节点上游的所有节点 */
function computeParentUpstreamNodes(startNodeId: string): WorkflowFlowNode[] {
  const edgeList = edges.value
  const nodeList = nodes.value
  if (!edgeList.length || !startNodeId) return []

  const reverseAdj = new Map<string, string[]>()
  for (const edge of edgeList) {
    const list = reverseAdj.get(edge.target)
    if (list) { list.push(edge.source) }
    else { reverseAdj.set(edge.target, [edge.source]) }
  }

  const visited = new Set<string>()
  const queue: string[] = [startNodeId]
  while (queue.length) {
    const nodeId = queue.shift()!
    if (visited.has(nodeId)) continue
    visited.add(nodeId)
    const sources = reverseAdj.get(nodeId)
    if (sources) {
      for (const source of sources) {
        if (!visited.has(source)) queue.push(source)
      }
    }
  }
  visited.delete(startNodeId)

  const result: WorkflowFlowNode[] = []
  for (const nodeId of visited) {
    const node = nodeList.find((n) => n.id === nodeId)
    if (node) result.push(node)
  }
  return result
}
</script>

<template>
  <main class="workflow-editor-shell">
    <!-- 子流程编辑模式横幅 -->
    <div v-if="subWorkflowActive" class="sub-workflow-banner">
      <span class="sub-workflow-banner-text">正在编辑「{{ subWorkflowParentLabel }}」的子流程</span>
    </div>

    <WorkflowCanvasViewport
      ref="canvasRef"
      v-model:nodes="nodes"
      v-model:edges="edges"
      :readonly="readonly"
      @select-node="selectedNodeId = $event"
      @node-context="openContextMenu"
      @pane-click="closeContextMenu"
      @show-library="openLibraryFromNode"
      @show-library-from-edge="openLibraryFromEdge"
      @delete-nodes="deleteSelectedNodes"
    />

    <WorkflowTopLeft
      v-if="!subWorkflowActive"
      :title="workflow.name || ''"
      :status="workflow.status"
      :version="workflow.version"
      :saving="saving"
      :save-state="saveState"
      :readonly="readonly"
      @back="goBack"
      @update-title="updateWorkflowTitle"
    />

    <!-- 子流程模式下的简化返回栏 -->
    <div v-if="subWorkflowActive" class="sub-workflow-topbar">
      <AButton type="text" class="sub-workflow-back-btn" @click="exitSubWorkflow">
        <template #icon><ArrowLeftOutlined /></template>
        完成编辑，返回主流程
      </AButton>
    </div>

    <WorkflowTopActions
      v-if="!readonly && !subWorkflowActive"
      :saving="saving"
      :running="running"
      @save="saveWorkflow"
      @validate="validateWorkflow"
      @publish="publishWorkflow"
      @debug="openDebugPanel"
      @versions="showVersions"
    />

    <WorkflowCanvasToolbar
      v-if="!readonly"
      :locked="Boolean(workflow.locked)"
      :can-undo="canUndo"
      :can-redo="canRedo"
      :has-nodes="nodes.length > 0"
      :library-open="libraryOpen"
      :lock-toggling="lockToggling"
      :hide-lock="subWorkflowActive"
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
      @clear-all-nodes="clearAllNodes"
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
      :edges="edges"
      :resources="resources"
      :right-offset="configPanelRightOffset"
      @update="updateNode"
      @close="selectedNodeId = null"
    />

    <WorkflowValidationPanel
      v-model:width="validationPanelWidth"
      :open="validationPanelOpen"
      :loading="validating"
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

    <AModal
      v-model:open="leaveConfirmOpen"
      title="确认退出编辑器"
      :closable="false"
      :mask-closable="false"
      :keyboard="false"
      style="width: 450px"
    >
      <p class="leave-confirm-message">{{ leaveConfirmMessage }}</p>
      <template #footer>
        <AButton :disabled="leavingAfterSave" @click="leaveDirectly">直接退出</AButton>
        <AButton type="primary" :loading="leavingAfterSave" @click="saveAndLeave">保存并退出</AButton>
      </template>
    </AModal>
  </main>
</template>

<style scoped lang="scss">
.workflow-editor-shell {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #f7f8fa;
}

.popover-mask {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.leave-confirm-message {
  margin: 0;
  color: #595959;
}

.sub-workflow-banner {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 16px;
  background: rgba(22, 119, 255, 0.08);
  backdrop-filter: blur(8px);
  border-radius: 0 0 8px 8px;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    left: -30%;
    width: 30%;
    background: linear-gradient(
      90deg,
      transparent,
      rgba(255, 255, 255, 0.6),
      transparent
    );
    transform: skewX(-16deg);
    animation: wave 1.5s ease-in-out infinite;
  }
}

.sub-workflow-banner-text {
  font-size: 13px;
  color: #1677ff;
  font-weight: 500;
  position: relative;
  z-index: 1;
}

@keyframes wave {
  0% { left: -30%; }
  100% { left: 130%; }
}

.sub-workflow-topbar {
  position: absolute;
  top: 12px;
  left: 16px;
  z-index: 10;
}

.sub-workflow-back-btn {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
  &:hover { color: #1677ff; }
}
</style>

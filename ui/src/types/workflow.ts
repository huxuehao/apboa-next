import type { Node, Edge } from '@vue-flow/core'

export type WorkflowStatus = 'DRAFT' | 'PUBLISHED'
export type WorkflowRunStatus = 'RUNNING' | 'SUCCESS' | 'FAIL' | 'STOP'
export type WorkflowNodeRunStatus = 'RUNNING' | 'SUCCESS' | 'FAIL' | 'STOP'
export type InputSourceType = 'CONSTANT' | 'VARIABLE' | 'NODE_OUTPUT' | 'EXPRESSION'

export interface PageQuery {
  page?: number
  size?: number
}

export interface WorkflowQuery extends PageQuery {
  name?: string
  status?: WorkflowStatus
  routeId?: string
  enabled?: boolean
}

export interface WorkflowRunQuery extends PageQuery {
  workflowId?: string
  routeId?: string
  status?: WorkflowRunStatus
}

export interface WorkflowDefinition {
  nodes: WorkflowNodeDefinition[]
  edges: WorkflowEdgeDefinition[]
  variables?: WorkflowVariable[]
  viewport?: { x: number; y: number; zoom: number }
  metadata?: {
    schemaVersion?: string
    updatedAt?: string
    nodeVersion?: string
  }
}

export interface WorkflowNodeDefinition {
  id: string
  type: string
  name: string
  position: { x: number; y: number }
  config: Record<string, unknown>
  inputConfigs?: WorkflowInputConfig[]
  outputConfigs?: WorkflowOutputConfig[]
  ui?: Record<string, unknown>
}

export interface WorkflowEdgeDefinition {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  label?: string
}

export interface WorkflowInputConfig {
  name: string
  sourceType: InputSourceType
  value?: unknown
  variableName?: string
  nodeId?: string
  outputName?: string
  expression?: string
}

export interface WorkflowOutputConfig {
  name: string
  fromNodeId?: string
  type?: string
  description?: string
}

export interface Workflow {
  id?: string
  tenantId?: string
  name?: string
  remark?: string
  routeId?: string
  status?: WorkflowStatus
  version?: string
  config?: WorkflowDefinition
  locked?: number
  enabled?: boolean
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

export interface WorkflowVersion {
  id: string
  name: string
  routeId?: string
  workflowId: string
  remark?: string
  config: WorkflowDefinition
  version: string
  createdAt?: string
}

export interface WorkflowRun {
  id: string
  routeId?: string
  workflowId: string
  version?: string
  config?: WorkflowDefinition
  status: WorkflowRunStatus
  inputs?: unknown
  outputs?: unknown
  error?: string
  startTime?: number
  endTime?: number
  createdAt?: string
}

export interface WorkflowNodeExecution {
  id: string
  routeId?: string
  workflowId: string
  workflowRunId: string
  nodeId: string
  nodeTitle?: string
  nodeType: string
  inputs?: string
  processData?: string
  outputs?: string
  status: WorkflowNodeRunStatus
  error?: string
  startTime?: number
  endTime?: number
}

export interface WorkflowRunRequest {
  params?: Array<{ name: string; value: unknown }>
  body?: unknown
  variables?: Record<string, unknown>
}

export interface WorkflowRunResult {
  run: WorkflowRun
  output?: unknown
  nodeExecutions: WorkflowNodeExecution[]
}

export interface WorkflowValidationResult {
  valid: boolean
  errors: Array<{ nodeId?: string; field?: string; message?: string } | string>
  warnings?: Array<{ nodeId?: string; field?: string; message?: string } | string>
}

export interface WorkflowResourceRefs {
  cacheIds?: string[]
  datasourceIds?: string[]
  mqIds?: string[]
  pluginIds?: string[]
}

export interface WorkflowDetail {
  workflow: Workflow
  resources?: WorkflowResourceRefs
}

export interface NodeMetadata {
  type: string
  title: string
  group: string
  description: string
  defaultConfig: Record<string, unknown>
  outputs: string[]
  branchable: boolean
}

export interface WorkflowResource {
  id: string
  name: string
  type?: string
  enabled?: boolean
}

/** 节点卡片 Summary 项类型 */
export type SummaryItemType = 'text' | 'badge' | 'tag' | 'code-snippet'

export interface SummaryItem {
  type: SummaryItemType
  label?: string
  value: unknown
  color?: string
}

export interface WorkflowNodeSchema {
  type: string
  title: string
  group: string
  groupTitle: string
  description: string
  icon: string
  color: string
  defaultConfig: Record<string, unknown>
  /** 节点专属配置面板组件名，对应 panels/nodes/ 下的 .vue 文件 */
  panelComponent: string
  /** 节点卡片底部摘要组件名，对应 node/summaries/ 下的 .vue 文件 */
  summaryComponent?: string
  /** 是否显示卡片底部摘要区域，默认 true。START/END 设为 false */
  showSummary?: boolean
  inputConfigs: WorkflowInputConfig[]
  outputConfigs: WorkflowOutputConfig[]
  branchHandles?: Array<{ id: string; label: string }>
}

export interface WorkflowResourceMaps {
  caches: WorkflowResource[]
  datasources: WorkflowResource[]
  mqs: WorkflowResource[]
}

export type VariableType = 'string' | 'number' | 'boolean' | 'object' | 'array'

export interface WorkflowVariable {
  id: string
  name: string
  type: VariableType
  source: 'system' | 'custom'
  description?: string
}

export type FlowNodeData = {
  type: string
  label: string
  description?: string
  status?: WorkflowNodeRunStatus | 'IDLE' | 'INVALID'
  errors?: string[]
  config: Record<string, unknown>
  inputConfigs?: WorkflowInputConfig[]
  outputConfigs?: WorkflowOutputConfig[]
  metadata?: NodeMetadata
  schema?: WorkflowNodeSchema
  resources?: WorkflowResourceMaps
}

export type WorkflowFlowNode = Node<FlowNodeData> & { data: FlowNodeData }
export type WorkflowFlowEdge = Edge

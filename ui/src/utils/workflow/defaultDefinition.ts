import {
  cloneDefaultConfig,
  cloneDefaultInputs,
  cloneDefaultOutputs,
  getWorkflowNodeSchema,
} from '@/config/workflow/nodeSchemas'
import type { WorkflowDefinition, WorkflowNodeDefinition } from '@/types/workflow'

function createNode(id: string, type: string, position: { x: number; y: number }): WorkflowNodeDefinition {
  const schema = getWorkflowNodeSchema(type)!
  return {
    id,
    type,
    name: schema.title,
    position,
    config: cloneDefaultConfig(schema),
    inputConfigs: cloneDefaultInputs(schema),
    outputConfigs: cloneDefaultOutputs(schema, id),
    ui: {},
  }
}

export function createDefaultWorkflowDefinition(): WorkflowDefinition {
  const start = createNode('start', 'START', { x: 120, y: 180 })
  const end = createNode('end', 'END', { x: 520, y: 180 })
  end.inputConfigs = [{ name: 'input', sourceType: 'NODE_OUTPUT', nodeId: 'start', outputName: 'output' }]
  return {
    nodes: [start, end],
    edges: [{ id: 'edge-start-end', source: 'start', target: 'end', sourceHandle: 'output', targetHandle: 'input', label: '' }],
    viewport: { x: 0, y: 0, zoom: 1 },
    metadata: { schemaVersion: '1.0', nodeVersion: '1.0', updatedAt: new Date().toISOString() },
  }
}

export function ensureWorkflowDefinition(definition?: WorkflowDefinition | null): WorkflowDefinition {
  if (!definition?.nodes?.length) return createDefaultWorkflowDefinition()
  const hasStart = definition.nodes.some((node) => node.type === 'START')
  const hasEnd = definition.nodes.some((node) => node.type === 'END')
  if (hasStart && hasEnd) return definition

  const fallback = createDefaultWorkflowDefinition()
  const fallbackStart = fallback.nodes.find((node) => node.type === 'START')!
  const fallbackEnd = fallback.nodes.find((node) => node.type === 'END')!
  const fallbackEdge = fallback.edges[0]!
  const nodes = [...definition.nodes]
  const edges = [...(definition.edges || [])]
  if (!hasStart) nodes.unshift(fallbackStart)
  if (!hasEnd) nodes.push(fallbackEnd)
  if (!hasStart && !hasEnd) edges.unshift(fallbackEdge)
  return {
    ...definition,
    nodes,
    edges,
    viewport: definition.viewport || fallback.viewport,
    metadata: { ...fallback.metadata, ...(definition.metadata || {}) },
  }
}

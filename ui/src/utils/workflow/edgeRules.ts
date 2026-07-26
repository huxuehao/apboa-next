import type { WorkflowFlowEdge, WorkflowFlowNode } from '@/types/workflow'

/** 连线校验结果：不通过时携带面向用户的原因说明 */
export interface EdgeRuleResult {
  ok: boolean
  reason?: string
}

/** 判断节点是否允许同一输出点引出多条连线（由 schema.multipleOutputs 声明） */
export function allowsMultipleOutputs(node: WorkflowFlowNode | null | undefined) {
  return Boolean(node?.data.schema?.multipleOutputs)
}

/** 判断节点指定输出点是否已有出边 */
export function hasOutgoingEdge(
  edges: WorkflowFlowEdge[],
  nodeId: string,
  sourceHandle = 'output',
) {
  return edges.some(
    (edge) => edge.source === nodeId && (edge.sourceHandle || 'output') === sourceHandle,
  )
}

/**
 * 校验能否从指定节点的输出点新增一条连线。
 * 规则：多输出节点（条件分支、结果匹配、意图识别）不限制；
 * 其余节点每个输出点最多允许一条出边，超出时拒绝并给出原因。
 */
export function validateOutputEdge(
  node: WorkflowFlowNode | null | undefined,
  edges: WorkflowFlowEdge[],
  sourceHandle = 'output',
): EdgeRuleResult {
  if (!node) return { ok: false, reason: '源节点不存在，无法建立连线' }
  if (allowsMultipleOutputs(node)) return { ok: true }
  if (hasOutgoingEdge(edges, node.id, sourceHandle)) {
    const label = node.data.label || node.data.schema?.title || node.id
    return {
      ok: false,
      reason: `「${label}」不支持多个输出`,
    }
  }
  return { ok: true }
}

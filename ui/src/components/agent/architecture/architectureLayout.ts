import type { Edge, Node, XYPosition } from '@vue-flow/core'

import { NODE_SIZES } from './types'

const DIRECT_NODE_TYPES = new Set([
  'model',
  'prompt',
  'advanced-config',
  'sensitive-item'
])

const CAPABILITY_START_ANGLE = -Math.PI - 0.65
const CAPABILITY_END_ANGLE = 0.65
const CATEGORY_SECTOR_GAP = 0.035
const MIN_CATEGORY_SECTOR_ANGLE = 0.34
const ITEM_SECTOR_INSET = 0.025
const CATEGORY_RADIUS_X = 380
const CATEGORY_RADIUS_Y = 220
const ITEM_RADIUS_X = 580
const ITEM_RADIUS_Y = 380
const ITEM_RING_GAP_X = 220
const ITEM_RING_GAP_Y = 160
const DIRECT_ROW_GAP_X = 40
const DIRECT_MODEL_TOP = 220
const DIRECT_CONFIG_TOP = 540
const COLLISION_GAP = 24
const MAX_LAYOUT_SCALE = 2.5
const MAX_ITEM_RINGS = 4
const TARGET_VIEWPORT_WIDTH = 1400
const TARGET_VIEWPORT_HEIGHT = 900

type HandleId = 'top' | 'right' | 'bottom' | 'left'

interface Bounds {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

interface CategoryBranch {
  category: Node
  children: Node[]
  startAngle: number
  endAngle: number
  angle: number
}

interface LayoutCandidate {
  nodes: Node[]
  score: number
}

function getNodeSize(node: Node): { width: number; height: number } {
  switch (node.type) {
    case 'center-agent':
      return NODE_SIZES.center
    case 'category':
      return NODE_SIZES.category
    case 'model':
      return NODE_SIZES.model
    case 'prompt':
      return NODE_SIZES.prompt
    case 'advanced-config':
      return NODE_SIZES.advanced
    default:
      return NODE_SIZES.item
  }
}

function getNodeCenter(node: Node): XYPosition {
  const size = getNodeSize(node)
  return {
    x: node.position.x + size.width / 2,
    y: node.position.y + size.height / 2
  }
}

function toNodePosition(center: XYPosition, node: Node): XYPosition {
  const size = getNodeSize(node)
  return {
    x: Math.round(center.x - size.width / 2),
    y: Math.round(center.y - size.height / 2)
  }
}

function getEllipsePoint(angle: number, radiusX: number, radiusY: number): XYPosition {
  return {
    x: Math.cos(angle) * radiusX,
    y: Math.sin(angle) * radiusY
  }
}

function getBounds(nodes: Node[]): Bounds | null {
  if (!nodes.length) return null

  return nodes.reduce<Bounds>((bounds, node) => {
    const size = getNodeSize(node)
    return {
      minX: Math.min(bounds.minX, node.position.x),
      minY: Math.min(bounds.minY, node.position.y),
      maxX: Math.max(bounds.maxX, node.position.x + size.width),
      maxY: Math.max(bounds.maxY, node.position.y + size.height)
    }
  }, {
    minX: Number.POSITIVE_INFINITY,
    minY: Number.POSITIVE_INFINITY,
    maxX: Number.NEGATIVE_INFINITY,
    maxY: Number.NEGATIVE_INFINITY
  })
}

function hasOverlap(nodes: Node[]): boolean {
  for (let i = 0; i < nodes.length; i += 1) {
    const current = nodes[i]!
    const currentSize = getNodeSize(current)

    for (let j = i + 1; j < nodes.length; j += 1) {
      const other = nodes[j]!
      const otherSize = getNodeSize(other)
      const separated = current.position.x + currentSize.width + COLLISION_GAP <= other.position.x
        || other.position.x + otherSize.width + COLLISION_GAP <= current.position.x
        || current.position.y + currentSize.height + COLLISION_GAP <= other.position.y
        || other.position.y + otherSize.height + COLLISION_GAP <= current.position.y

      if (!separated) return true
    }
  }

  return false
}

function createCategoryBranches(nodes: Node[], edges: Edge[]): CategoryBranch[] {
  const nodeMap = new Map(nodes.map(node => [node.id, node]))
  const categoryNodes = nodes.filter(node => node.type === 'category')
  const childCounts = categoryNodes.map(category => (
    edges.filter(edge => edge.source === category.id && nodeMap.has(edge.target)).length
  ))
  const totalChildCount = childCounts.reduce((sum, count) => sum + count, 0)
  const totalGap = Math.max(categoryNodes.length - 1, 0) * CATEGORY_SECTOR_GAP
  const usableAngle = CAPABILITY_END_ANGLE - CAPABILITY_START_ANGLE - totalGap
  const minimumSectorAngle = categoryNodes.length > 0
    ? Math.min(MIN_CATEGORY_SECTOR_ANGLE, usableAngle / categoryNodes.length)
    : 0
  const weightedAngle = Math.max(usableAngle - minimumSectorAngle * categoryNodes.length, 0)
  let currentAngle = CAPABILITY_START_ANGLE

  return categoryNodes.map((category, index) => {
    const children = edges
      .filter(edge => edge.source === category.id)
      .map(edge => nodeMap.get(edge.target))
      .filter((node): node is Node => !!node && !DIRECT_NODE_TYPES.has(node.type || ''))
    const sectorAngle = minimumSectorAngle + (totalChildCount > 0
      ? weightedAngle * childCounts[index]! / totalChildCount
      : 0)
    const startAngle = currentAngle
    const endAngle = startAngle + sectorAngle
    currentAngle = endAngle + CATEGORY_SECTOR_GAP

    return {
      category,
      children,
      startAngle,
      endAngle,
      angle: (startAngle + endAngle) / 2
    }
  })
}

function splitChildrenByRing(children: Node[], ringCount: number): Node[][] {
  const actualRingCount = Math.min(Math.max(ringCount, 1), children.length)
  const groups: Node[][] = []
  let offset = 0

  for (let ringIndex = 0; ringIndex < actualRingCount; ringIndex += 1) {
    const remainingChildren = children.length - offset
    const remainingRings = actualRingCount - ringIndex
    const groupSize = Math.ceil(remainingChildren / remainingRings)
    groups.push(children.slice(offset, offset + groupSize))
    offset += groupSize
  }

  return groups
}

function buildCapabilityPositions(
  centerNode: Node,
  branches: CategoryBranch[],
  ringCount: number,
  scale: number
): Map<string, XYPosition> {
  const positions = new Map<string, XYPosition>()
  positions.set(centerNode.id, toNodePosition({ x: 0, y: 0 }, centerNode))

  branches.forEach((branch) => {
    const categoryCenter = getEllipsePoint(
      branch.angle,
      CATEGORY_RADIUS_X * scale,
      CATEGORY_RADIUS_Y * scale
    )
    positions.set(branch.category.id, toNodePosition(categoryCenter, branch.category))

    const groups = splitChildrenByRing(branch.children, ringCount)
    groups.forEach((children, ringIndex) => {
      const startAngle = branch.startAngle + ITEM_SECTOR_INSET
      const endAngle = branch.endAngle - ITEM_SECTOR_INSET
      const availableAngle = Math.max(endAngle - startAngle, 0)
      const radiusX = (ITEM_RADIUS_X + ringIndex * ITEM_RING_GAP_X) * scale
      const radiusY = (ITEM_RADIUS_Y + ringIndex * ITEM_RING_GAP_Y) * scale

      children.forEach((child, childIndex) => {
        const ratio = children.length === 1 ? 0.5 : (childIndex + 0.5) / children.length
        const ringShift = children.length === 1 && groups.length > 1
          ? (ringIndex - (groups.length - 1) / 2) * Math.min(availableAngle * 0.08, 0.04)
          : 0
        const angle = startAngle + availableAngle * ratio + ringShift
        const itemCenter = getEllipsePoint(angle, radiusX, radiusY)
        positions.set(child.id, toNodePosition(itemCenter, child))
      })
    })
  })

  return positions
}

function layoutRow(nodes: Node[], top: number): Map<string, XYPosition> {
  const positions = new Map<string, XYPosition>()
  const rowWidth = nodes.reduce((sum, node) => sum + getNodeSize(node).width, 0)
    + Math.max(nodes.length - 1, 0) * DIRECT_ROW_GAP_X
  let currentX = -rowWidth / 2

  nodes.forEach((node) => {
    positions.set(node.id, { x: Math.round(currentX), y: top })
    currentX += getNodeSize(node).width + DIRECT_ROW_GAP_X
  })

  return positions
}

function layoutDirectNodes(nodes: Node[]): Map<string, XYPosition> {
  const modelNodes = nodes.filter(node => node.type === 'model')
  const configNodes = nodes.filter(node => node.type !== 'model')
  return new Map([
    ...layoutRow(modelNodes, DIRECT_MODEL_TOP),
    ...layoutRow(configNodes, DIRECT_CONFIG_TOP)
  ])
}

function applyPositions(nodes: Node[], positions: Map<string, XYPosition>): Node[] {
  return nodes.map((node) => {
    const position = positions.get(node.id)
    return position ? { ...node, position } : node
  })
}

function getLayoutScore(nodes: Node[], ringCount: number, scale: number): number {
  const bounds = getBounds(nodes)
  if (!bounds) return 0

  const width = bounds.maxX - bounds.minX
  const height = bounds.maxY - bounds.minY
  const fitScale = Math.max(width / TARGET_VIEWPORT_WIDTH, height / TARGET_VIEWPORT_HEIGHT)
  return fitScale + ringCount * 0.005 + scale * 0.001
}

/**
 * 保留“智能体居中、能力围绕中心开花”的视觉结构：分类按子节点数量分配扇区，
 * 子节点在一到多层椭圆环中展开，下方为模型配置预留空间，并排除节点重叠。
 */
export function layoutArchitectureNodes(nodes: Node[], edges: Edge[]): Node[] {
  if (!nodes.length) return []

  const centerNode = nodes.find(node => node.type === 'center-agent')
  if (!centerNode) return nodes

  const directNodes = nodes.filter(node => DIRECT_NODE_TYPES.has(node.type || ''))
  const capabilityNodes = nodes.filter(node => !DIRECT_NODE_TYPES.has(node.type || ''))
  const directPositions = layoutDirectNodes(directNodes)
  const branches = createCategoryBranches(capabilityNodes, edges)
  const totalItemCount = branches.reduce((sum, branch) => sum + branch.children.length, 0)
  const maxRingCount = Math.min(MAX_ITEM_RINGS, Math.max(totalItemCount, 1))
  let bestCandidate: LayoutCandidate | null = null

  for (let ringCount = 1; ringCount <= maxRingCount; ringCount += 1) {
    for (let scaleStep = 0; scaleStep <= 30; scaleStep += 1) {
      const scale = 1 + scaleStep * 0.05
      if (scale > MAX_LAYOUT_SCALE) break

      const capabilityPositions = buildCapabilityPositions(centerNode, branches, ringCount, scale)
      const laidOutCapabilityNodes = applyPositions(capabilityNodes, capabilityPositions)
      const laidOutNodes = applyPositions([...laidOutCapabilityNodes, ...directNodes], directPositions)
      if (hasOverlap(laidOutNodes)) continue

      const score = getLayoutScore(laidOutNodes, ringCount, scale)
      if (!bestCandidate || score < bestCandidate.score) {
        bestCandidate = { nodes: laidOutNodes, score }
      }
      break
    }
  }

  if (bestCandidate) return bestCandidate.nodes

  const fallbackPositions = buildCapabilityPositions(centerNode, branches, maxRingCount, MAX_LAYOUT_SCALE)
  const fallbackCapabilityNodes = applyPositions(capabilityNodes, fallbackPositions)
  return applyPositions([...fallbackCapabilityNodes, ...directNodes], directPositions)
}

/**
 * 径向布局中的连线使用离目标最近的四向连接点，避免连线绕过节点卡片。
 */
export function getArchitectureEdgeHandles(sourceNode: Node, targetNode: Node): {
  sourceHandle: HandleId
  targetHandle: HandleId
} {
  const sourceCenter = getNodeCenter(sourceNode)
  const targetCenter = getNodeCenter(targetNode)
  const deltaX = targetCenter.x - sourceCenter.x
  const deltaY = targetCenter.y - sourceCenter.y

  if (Math.abs(deltaX) >= Math.abs(deltaY)) {
    return deltaX >= 0
      ? { sourceHandle: 'right', targetHandle: 'left' }
      : { sourceHandle: 'left', targetHandle: 'right' }
  }

  return deltaY >= 0
    ? { sourceHandle: 'bottom', targetHandle: 'top' }
    : { sourceHandle: 'top', targetHandle: 'bottom' }
}

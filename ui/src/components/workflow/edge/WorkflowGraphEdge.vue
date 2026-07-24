<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@vue-flow/core'

const props = defineProps<EdgeProps & { locked?: boolean }>()

const emit = defineEmits<{
  'add-node': [payload: { edgeId: string; x: number; y: number }]
}>()

const hovered = ref(false)
let hideTimer: ReturnType<typeof setTimeout> | null = null

const pathParams = computed(() => ({
  sourceX: props.sourceX,
  sourceY: props.sourceY,
  sourcePosition: props.sourcePosition,
  targetX: props.targetX,
  targetY: props.targetY,
  targetPosition: props.targetPosition,
}))

const edgePath = computed(() => getBezierPath(pathParams.value)[0])
const labelX = computed(() => getBezierPath(pathParams.value)[1])
const labelY = computed(() => getBezierPath(pathParams.value)[2])

// 选中时描边改为蓝色，其余样式沿用 VueFlow 传入的 style
const edgeStyle = computed(() => ({
  ...(props.style as Record<string, unknown>),
  ...(props.selected ? { stroke: '#1677ff' } : {}),
}))

function showAction() {
  if (props.locked) return
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
  hovered.value = true
}

function hideActionSoon() {
  if (hideTimer) clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    hovered.value = false
    hideTimer = null
  }, 120)
}

function onAddClick(event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  event.stopImmediatePropagation()
  if (props.locked) return
  emit('add-node', { edgeId: props.id, x: event.clientX, y: event.clientY })
}

onBeforeUnmount(() => {
  if (hideTimer) clearTimeout(hideTimer)
})
</script>

<template>
  <BaseEdge
    :id="id"
    :path="edgePath"
    :marker-start="markerStart"
    :marker-end="markerEnd"
    :style="edgeStyle"
    :interaction-width="interactionWidth"
  />
  <path
    :d="edgePath"
    class="workflow-edge-hit"
    @mouseenter="showAction"
    @mouseleave="hideActionSoon"
  />
  <EdgeLabelRenderer>
    <button
      v-if="hovered && !locked"
      type="button"
      class="edge-add-button"
      :style="{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }"
      aria-label="在连线中插入节点"
      @mouseenter="showAction"
      @mouseleave="hideActionSoon"
      @pointerdown.stop.prevent
      @mousedown.stop.prevent
      @click="onAddClick"
    />
  </EdgeLabelRenderer>
</template>

<style scoped lang="scss">
.workflow-edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 18px;
  pointer-events: stroke;
}

.edge-add-button {
  position: absolute;
  z-index: 6;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 18 18'%3E%3Crect x='3' y='8' width='12' height='2' rx='1' fill='white'/%3E%3Crect x='8' y='3' width='2' height='12' rx='1' fill='white'/%3E%3C/svg%3E") no-repeat center / 18px 18px, #1677ff;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(22, 119, 255, 0.28);
  pointer-events: all;
}

.edge-add-button:hover {
  background-color: #4096ff;
}
</style>

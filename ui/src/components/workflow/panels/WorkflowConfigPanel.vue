<script setup lang="ts">
import { computed, ref, provide, defineAsyncComponent, watch } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import IconFont from '@/components/common/IconFont.vue'
import QuickInputPopup from './shared/QuickInputPopup.vue'
import { useQuickInput } from './shared/useQuickInput'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'
import { getNodeIconName } from '@/config/workflow/common'

const props = defineProps<{
  node: WorkflowFlowNode | null
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
  rightOffset?: number
}>()

defineEmits<{
  update: [node: WorkflowFlowNode]
  close: []
}>()

const width = ref(442)
const dragging = ref(false)

const maxWidth = computed(() =>
  Math.max(442, Math.floor((window.innerWidth - (props.rightOffset || 16) - 48) * 0.55)),
)

const nodeColor = computed(() => props.node?.data.schema?.color || '#1677ff')

provide('workflowEdges', computed(() => props.edges))

// 快捷输入（双击 Ctrl 插入输入绑定名）
const {
  isPopupVisible,
  popupItems,
  anchorElement,
  setInputNames,
  handleSelect,
  closePopup,
} = useQuickInput()

// 监听节点切换，更新输入绑定名称列表
watch(
  () => props.node?.data.inputConfigs,
  (configs) => {
    if (configs && configs.length > 0) {
      setInputNames(configs.map((c) => c.name).filter(Boolean))
    } else {
      setInputNames([])
    }
  },
  { immediate: true },
)

// 缓存 defineAsyncComponent 结果，相同组件名始终返回同一引用，防止 Vue 因引用变化而销毁重建组件
const asyncPanelCache = new Map<string, ReturnType<typeof defineAsyncComponent>>()

const panelComponent = computed(() => {
  const name = props.node?.data.schema?.panelComponent
  if (!name) return null
  if (!asyncPanelCache.has(name)) {
    asyncPanelCache.set(name, defineAsyncComponent(() => import(`./nodes/${name}.vue`)))
  }
  return asyncPanelCache.get(name)!
})

function beginResize(event: MouseEvent) {
  dragging.value = true
  const startX = event.clientX
  const startWidth = width.value
  const onMove = (moveEvent: MouseEvent) => {
    const next = startWidth + (startX - moveEvent.clientX)
    width.value = Math.max(442, Math.min(maxWidth.value, next))
  }
  const onUp = () => {
    dragging.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
</script>

<template>
  <aside
    v-if="node"
    class="config-panel"
    :class="{ dragging }"
    :style="{ width: `${width}px`, right: `${rightOffset || 16}px` }"
  >
    <div class="resize-handle" @mousedown.prevent="beginResize" />

    <header class="panel-header">
      <div class="panel-title-wrap">
        <span class="panel-avatar" :style="{ backgroundColor: `${nodeColor}CC` }">
          <IconFont :name="getNodeIconName(node.data.type)" :size="16" color="#ffffff" />
        </span>
        <div class="panel-title">{{ node.data.schema?.title || node.data.type }}</div>
      </div>
      <AButton type="text" @click="$emit('close')">
        <template #icon><CloseOutlined /></template>
      </AButton>
    </header>

    <div class="panel-body">
      <Suspense>
        <component
          v-if="panelComponent"
          :is="panelComponent"
          :node="node"
          :nodes="nodes"
          :resources="resources"
          :edges="edges"
          @update="(updatedNode: WorkflowFlowNode) => $emit('update', updatedNode)"
        />
      </Suspense>
    </div>

    <QuickInputPopup
      :visible="isPopupVisible"
      :items="popupItems"
      :anchor-el="anchorElement"
      @select="handleSelect"
      @close="closePopup"
    />
  </aside>
</template>

<style scoped lang="scss">
.config-panel {
  position: absolute;
  top: 60px;
  bottom: 18px;
  z-index: 16;
  min-width: 442px;
  max-width: 50vw;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}
.resize-handle {
  position: absolute;
  left: -5px;
  top: 0;
  bottom: 0;
  width: 3px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s ease;
}
.resize-handle::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 40px;
  border-radius: 1px;
  background: #c9c9c9;
  transition: background 0.2s ease;
}
.resize-handle:hover::after,
.dragging .resize-handle::after {
  display: none;
}
.resize-handle:hover,
.dragging .resize-handle {
  background: #1677ff;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.panel-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  flex-shrink: 0;
}
.panel-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
  font-size: 15px;
  font-weight: 700;
}
.panel-body {
  min-height: 0;
  overflow: auto;
  padding: 12px 16px 18px;
}
@media (max-width: 900px) {
  .config-panel {
    left: 12px;
    right: 12px;
    top: auto;
    width: auto !important;
    min-width: 0;
    max-width: none;
    height: 70vh;
  }
  .resize-handle {
    display: none;
  }
}
</style>

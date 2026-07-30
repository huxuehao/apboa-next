<script setup lang="ts">
/**
 * 设计器画布：可拖拽/缩放的编辑态栅格，支持选中与删除面板。
 *
 * @author huxuehao
 */
import { ref, watch } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import { CloseOutlined } from '@ant-design/icons-vue'
import PanelRenderer from './PanelRenderer.vue'
import type { DashboardDsl, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  dsl: DashboardDsl
  selectedId: string | null
  globalParams?: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'select', id: string): void
  (e: 'remove', id: string): void
  (e: 'change'): void
}>()

interface LayoutItem {
  i: string
  x: number
  y: number
  w: number
  h: number
}

function buildLayout(): LayoutItem[] {
  return (props.dsl.panels || []).map((p) => ({
    i: p.id,
    x: p.layout.x,
    y: p.layout.y,
    w: p.layout.w,
    h: p.layout.h,
  }))
}

const layout = ref<LayoutItem[]>(buildLayout())

function panelById(id: string): PanelDsl | undefined {
  return props.dsl.panels.find((p) => p.id === id)
}

// 仅在面板增删（id 集合变化）时重建布局，拖拽改坐标不触发重建，避免循环
watch(
  () => props.dsl.panels.map((p) => p.id).join(','),
  () => {
    layout.value = buildLayout()
  },
)

// 拖拽/缩放改变坐标后回写到面板 DSL
watch(
  layout,
  (items) => {
    items.forEach((item) => {
      const panel = panelById(item.i)
      if (panel) {
        panel.layout = { x: item.x, y: item.y, w: item.w, h: item.h }
      }
    })
    emit('change')
  },
  { deep: true },
)

const colNum = props.dsl.grid?.cols || 24
const rowHeight = props.dsl.grid?.rowHeight || 40
const margin = props.dsl.grid?.margin || [12, 12]
</script>

<template>
  <GridLayout
    v-model:layout="layout"
    :col-num="colNum"
    :row-height="rowHeight"
    :margin="margin"
    :is-draggable="true"
    :is-resizable="true"
    :vertical-compact="false"
  >
    <GridItem
      v-for="item in layout"
      :key="item.i"
      :x="item.x"
      :y="item.y"
      :w="item.w"
      :h="item.h"
      :i="item.i"
    >
      <div
        class="canvas-item"
        :class="{ selected: item.i === selectedId }"
        @click="emit('select', item.i)"
      >
        <button
          v-if="item.i === selectedId"
          class="remove-btn"
          title="删除面板"
          @click.stop="emit('remove', item.i)"
        >
          <CloseOutlined />
        </button>
        <PanelRenderer v-if="panelById(item.i)" :panel="panelById(item.i)!" :global-params="globalParams" :interactive="false" />
      </div>
    </GridItem>
  </GridLayout>
</template>

<style scoped lang="scss">
.canvas-item {
  position: relative;
  height: 100%;
  cursor: move;
}

.canvas-item.selected {
  outline: 2px solid #1677ff;
  outline-offset: -1px;
  border-radius: 8px;
}

.remove-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  background: #fff;
  color: #999;
  cursor: pointer;
}

.remove-btn:hover {
  color: #cf1322;
  border-color: #cf1322;
}
</style>

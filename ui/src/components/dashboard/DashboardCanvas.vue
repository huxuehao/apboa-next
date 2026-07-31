<script setup lang="ts">
/**
 * 设计器画布：可拖拽/缩放的编辑态栅格，支持选中与删除面板。
 *
 * @author huxuehao
 */
import { onMounted, ref, watch } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import { CloseOutlined } from '@ant-design/icons-vue'
import PanelRenderer from './PanelRenderer.vue'
import type { DashboardDsl, PanelDsl } from '@/types/dashboard'

const props = withDefaults(
  defineProps<{
    dsl: DashboardDsl
    selectedId: string | null
    /** 防碰撞：开启时面板只能拖到空位；关闭时可拖到任意位置且其他面板自动归位 */
    preventCollision?: boolean
  }>(),
  { preventCollision: true },
)

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

// 拖拽/缩放改变坐标后回写到面板 DSL；
// 仅在坐标真正变化时回写并通知，避免挂载/首帧内部规范化触发假变更（导致误判为未保存）
watch(
  layout,
  (items) => {
    let changed = false
    items.forEach((item) => {
      const panel = panelById(item.i)
      if (
        panel &&
        (panel.layout.x !== item.x ||
          panel.layout.y !== item.y ||
          panel.layout.w !== item.w ||
          panel.layout.h !== item.h)
      ) {
        panel.layout = { x: item.x, y: item.y, w: item.w, h: item.h }
        changed = true
      }
    })
    if (changed) emit('change')
  },
  { deep: true },
)

const colNum = props.dsl.grid?.cols || 48
const rowHeight = props.dsl.grid?.rowHeight || 20
const margin = props.dsl.grid?.margin || [12, 12]

// 首帧禁用进入过渡：避免容器宽度未测量时卡片从左上角“缩放展开”
const ready = ref(false)
onMounted(() => {
  requestAnimationFrame(() => requestAnimationFrame(() => (ready.value = true)))
})
</script>

<template>
  <GridLayout
    v-model:layout="layout"
    :class="{ 'grid-booting': !ready }"
    :col-num="colNum"
    :row-height="rowHeight"
    :margin="margin"
    :is-draggable="true"
    :is-resizable="true"
    :vertical-compact="false"
    :prevent-collision="preventCollision"
    :restore-on-drag="!preventCollision"
    :is-bounded="true"
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
        <PanelRenderer v-if="panelById(item.i)" :panel="panelById(item.i)!" :interactive="false" :selected="item.i === selectedId" />
      </div>
    </GridItem>
  </GridLayout>
</template>

<style scoped lang="scss">
/* 首帧：隐藏并禁用 item 过渡，避免从左上角缩放展开；ready 后瞬间显示、恢复拖拽过渡 */
.grid-booting {
  opacity: 0;
}

.grid-booting :deep(.vgl-item) {
  transition: none !important;
}

/* 拖拽占位图形：默认红色方块，改为蓝色系 + 圆角 */
:deep(.vgl-item--placeholder) {
  background-color: #1677ff !important;
  opacity: 0.16 !important;
  border-radius: 8px;
}

.canvas-item {
  position: relative;
  height: 100%;
  cursor: move;
  user-select: none;
  -webkit-user-select: none;
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

/* 缩放手柄：grid-layout-plus 默认的直角 L 拐角过硬，改为圆角拐角，悬停高亮。
   注意：.vgl-layout 是本组件根元素，:deep(.vgl-layout) 无法命中，尺寸直接覆盖 resizer 元素 */
:deep(.vgl-item__resizer) {
  width: 14px;
  height: 14px;
}

:deep(.vgl-item__resizer)::before {
  right: 5px;
  bottom: 5px;
  border-color: #c8ccd4;
  border-bottom-right-radius: 8px;
  transition: border-color 0.2s ease;
}

:deep(.vgl-item:hover .vgl-item__resizer)::before {
  border-color: #1677ff;
}
</style>

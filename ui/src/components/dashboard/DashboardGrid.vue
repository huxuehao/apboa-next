<script setup lang="ts">
/**
 * 门户只读栅格：按 DSL 渲染面板，禁用拖拽与缩放。
 *
 * @author huxuehao
 */
import { computed, onMounted, ref } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import PanelRenderer from './PanelRenderer.vue'
import type { DashboardDsl, PanelDsl } from '@/types/dashboard'

const props = defineProps<{ dsl: DashboardDsl }>()

// 首帧禁用进入过渡：避免容器宽度未测量时卡片从左上角“缩放展开”
const ready = ref(false)
onMounted(() => {
  requestAnimationFrame(() => requestAnimationFrame(() => (ready.value = true)))
})

const layout = computed(() =>
  (props.dsl.panels || []).map((p) => ({
    i: p.id,
    x: p.layout.x,
    y: p.layout.y,
    w: p.layout.w,
    h: p.layout.h,
  })),
)

const panelMap = computed<Record<string, PanelDsl>>(() =>
  Object.fromEntries((props.dsl.panels || []).map((p) => [p.id, p])),
)

const colNum = computed(() => props.dsl.grid?.cols || 48)
const rowHeight = computed(() => props.dsl.grid?.rowHeight || 20)
const margin = computed(() => props.dsl.grid?.margin || [12, 12])
</script>

<template>
  <GridLayout
    :class="{ 'grid-booting': !ready }"
    :layout="layout"
    :col-num="colNum"
    :row-height="rowHeight"
    :margin="margin"
    :is-draggable="false"
    :is-resizable="false"
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
      <PanelRenderer :panel="panelMap[item.i]!" :global-refresh="dsl.refresh" :interactive="true" />
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
</style>

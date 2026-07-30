<script setup lang="ts">
/**
 * 门户只读栅格：按 DSL 渲染面板，禁用拖拽与缩放。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import PanelRenderer from './PanelRenderer.vue'
import type { DashboardDsl, PanelDsl } from '@/types/dashboard'

const props = defineProps<{ dsl: DashboardDsl; globalParams?: Record<string, unknown> }>()

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

const colNum = computed(() => props.dsl.grid?.cols || 24)
const rowHeight = computed(() => props.dsl.grid?.rowHeight || 40)
const margin = computed(() => props.dsl.grid?.margin || [12, 12])
</script>

<template>
  <GridLayout
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
      <PanelRenderer :panel="panelMap[item.i]!" :global-refresh="dsl.refresh" :global-params="globalParams" />
    </GridItem>
  </GridLayout>
</template>

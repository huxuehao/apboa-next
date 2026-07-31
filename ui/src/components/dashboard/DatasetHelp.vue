<script setup lang="ts">
/**
 * 数据集帮助入口：右下角可拖拽的悬浮 "?" 球，点击打开使用说明抽屉（按面板分组，注册表驱动）。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { QuestionOutlined } from '@ant-design/icons-vue'
import { registerBuiltinGuides, listGuides } from './guide'

registerBuiltinGuides()

const guides = listGuides()
const open = ref(false)
const activeKey = ref(guides[0]?.key || '')
const activeGuide = computed(() => guides.find((g) => g.key === activeKey.value) || guides[0])

// ── 悬浮球拖拽 ──
const BALL = 44
const x = ref(0)
const y = ref(0)
let moved = false

function clamp() {
  x.value = Math.min(Math.max(8, x.value), window.innerWidth - BALL - 8)
  y.value = Math.min(Math.max(8, y.value), window.innerHeight - BALL - 8)
}

function onDown(e: MouseEvent) {
  moved = false
  const startX = e.clientX
  const startY = e.clientY
  const originX = x.value
  const originY = y.value
  const onMove = (me: MouseEvent) => {
    const dx = me.clientX - startX
    const dy = me.clientY - startY
    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) moved = true
    x.value = originX + dx
    y.value = originY + dy
    clamp()
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    // 未发生拖动视为点击，打开抽屉
    if (!moved) open.value = true
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

function onResize() {
  clamp()
}

onMounted(() => {
  // 默认停靠右下角
  x.value = window.innerWidth - BALL - 24
  y.value = window.innerHeight - BALL - 24
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <button
    class="help-ball"
    :style="{ left: x + 'px', top: y + 'px' }"
    title="数据集使用说明（可拖动）"
    @mousedown.prevent="onDown"
  >
    <QuestionOutlined />
  </button>

  <a-drawer v-model:open="open" title="数据集使用说明" placement="right" width="50vw">
    <div class="guide-layout">
      <nav class="guide-nav">
        <button
          v-for="g in guides"
          :key="g.key"
          class="nav-item"
          :class="{ active: g.key === activeKey }"
          @click="activeKey = g.key"
        >
          {{ g.title }}
        </button>
      </nav>
      <div class="guide-body">
        <component :is="activeGuide.component" v-if="activeGuide" />
      </div>
    </div>
  </a-drawer>
</template>

<style scoped lang="scss">
.help-ball {
  position: fixed;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid #eceef1;
  border-radius: 50%;
  background: #fff;
  color: #1677ff;
  font-size: 18px;
  cursor: grab;
  user-select: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.help-ball:active {
  cursor: grabbing;
}

.guide-layout {
  display: flex;
  height: 100%;
  gap: 0;
}

.guide-nav {
  flex-shrink: 0;
  width: 150px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 12px;
  border-right: 1px solid #f0f0f0;
  overflow: auto;
}

.nav-item {
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #595959;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.nav-item:hover {
  background: #f5f5f5;
}

.nav-item.active {
  background: #f0f5ff;
  color: #1677ff;
  font-weight: 600;
}

.guide-body {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding-left: 16px;
}
</style>

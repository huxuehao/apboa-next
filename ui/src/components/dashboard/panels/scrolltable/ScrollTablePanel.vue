<script setup lang="ts">
/**
 * 滚动轮播表：数据集行超出可视行数时自动纵向轮播滚动，无缝循环。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  data: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const ROW_HEIGHT = 36

const columns = computed(() => props.data?.columns || [])
const rows = computed(() => props.data?.rows || [])
const rowsPerView = computed(() => (props.panel.options?.rowsPerView as number) || 5)
const interval = computed(() => (props.panel.options?.interval as number) || 2000)
const showHeader = computed(() => props.panel.options?.showHeader !== false)
const viewportHeight = computed(() => rowsPerView.value * ROW_HEIGHT)
const canScroll = computed(() => rows.value.length > rowsPerView.value)
// 无缝循环：追加一份数据副本
const trackRows = computed(() => (canScroll.value ? [...rows.value, ...rows.value] : rows.value))

const step = ref(0)
const noTransition = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function start() {
  stop()
  step.value = 0
  if (!canScroll.value) return
  timer = setInterval(() => {
    step.value += 1
  }, Math.max(800, interval.value))
}

/** 滚过一整轮后无动画归零，实现无缝循环 */
function onTransitionEnd() {
  if (step.value >= rows.value.length) {
    noTransition.value = true
    step.value -= rows.value.length
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        noTransition.value = false
      }),
    )
  }
}

watch([() => rows.value.length, interval, rowsPerView], () => start())
onMounted(start)
onBeforeUnmount(stop)
</script>

<template>
  <div class="scroll-table">
    <div v-if="error" class="st-error">{{ error }}</div>
    <template v-else>
      <div v-if="showHeader" class="st-row st-head">
        <div v-for="c in columns" :key="c.name" class="st-cell">{{ c.name }}</div>
      </div>
      <div class="st-viewport" :style="{ height: viewportHeight + 'px' }">
        <div
          class="st-track"
          :class="{ 'no-trans': noTransition }"
          :style="{ transform: `translateY(${-step * ROW_HEIGHT}px)` }"
          @transitionend="onTransitionEnd"
        >
          <div
            v-for="(r, i) in trackRows"
            :key="i"
            class="st-row"
            :style="{ height: ROW_HEIGHT + 'px' }"
          >
            <div v-for="c in columns" :key="c.name" class="st-cell">{{ r[c.name] }}</div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.scroll-table {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.st-viewport {
  overflow: hidden;
}

.st-track {
  transition: transform 0.5s linear;

  &.no-trans {
    transition: none;
  }
}

.st-row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #f5f5f5;
}

.st-head {
  height: 36px;
  background: #fafafa;
  font-weight: 600;
  color: #8c8c8c;
}

.st-cell {
  flex: 1;
  min-width: 0;
  padding: 0 12px;
  font-size: 13px;
  color: #434343;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.st-error {
  font-size: 13px;
  color: #cf1322;
}
</style>

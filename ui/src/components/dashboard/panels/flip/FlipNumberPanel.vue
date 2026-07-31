<script setup lang="ts">
/**
 * 数字翻牌：取数据集数值（或静态值），带 count-up 动画的数字翻牌展示。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  data: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const rows = computed(() => props.data?.rows || [])
const mapping = computed(() => props.panel.fieldMapping || {})

const label = computed(() => (props.panel.options?.label as string) || '')
const prefix = computed(() => (props.panel.options?.prefix as string) || '')
const suffix = computed(() => (props.panel.options?.suffix as string) || '')
const precision = computed(() => Number(props.panel.options?.precision) || 0)
const thousand = computed(() => props.panel.options?.thousand === true)

const target = computed<number>(() => {
  const field = mapping.value.value as string | undefined
  const row = rows.value[0]
  if (field && row) {
    const n = Number(row[field])
    if (!Number.isNaN(n)) return n
  }
  const sv = Number(props.panel.options?.value)
  return Number.isNaN(sv) ? 0 : sv
})

const display = ref(0)
let raf: number | null = null

function animateTo(to: number) {
  const from = display.value
  const start = performance.now()
  const dur = 800
  if (raf) cancelAnimationFrame(raf)
  const tick = (now: number) => {
    const p = Math.min(1, (now - start) / dur)
    const eased = 1 - Math.pow(1 - p, 3)
    display.value = from + (to - from) * eased
    if (p < 1) raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
}

watch(target, (v) => animateTo(v), { immediate: true })
onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf)
})

const formatted = computed(() => {
  let s = display.value.toFixed(precision.value)
  if (thousand.value) {
    const parts = s.split('.')
    const int = parts[0] ?? ''
    const dec = parts[1]
    s = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (dec ? '.' + dec : '')
  }
  return s
})

const chars = computed(() => formatted.value.split(''))

function isDigit(ch: string): boolean {
  return /\d/.test(ch)
}
</script>

<template>
  <div class="flip-panel">
    <div v-if="error" class="flip-error">{{ error }}</div>
    <template v-else>
      <div class="flip-number">
        <span v-if="prefix" class="flip-affix">{{ prefix }}</span>
        <span v-for="(ch, i) in chars" :key="i" :class="isDigit(ch) ? 'flip-digit' : 'flip-sep'">
          {{ ch }}
        </span>
        <span v-if="suffix" class="flip-affix">{{ suffix }}</span>
      </div>
      <div v-if="label" class="flip-label">{{ label }}</div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.flip-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 100%;
}

.flip-number {
  display: flex;
  align-items: center;
  gap: 4px;
}

.flip-digit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  padding: 6px 4px;
  border-radius: 4px;
  background: #262626;
  color: #fff;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}

.flip-sep {
  color: #262626;
  font-size: 24px;
  font-weight: 700;
}

.flip-affix {
  color: #595959;
  font-size: 16px;
}

.flip-label {
  font-size: 13px;
  color: #999;
}

.flip-error {
  font-size: 13px;
  color: #cf1322;
}
</style>

<script setup lang="ts">
/**
 * 时钟面板：实时时钟，支持数字/翻牌/模拟/日期时间多种样式。无数据集依赖。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  data?: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const style = computed(() => (props.panel.options?.style as string) || 'digital')
const showSeconds = computed(() => props.panel.options?.showSeconds !== false)
const showDate = computed(() => props.panel.options?.showDate !== false)

const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

function pad(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

const WEEK = ['日', '一', '二', '三', '四', '五', '六']

const timeStr = computed(() => {
  const d = now.value
  const base = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return showSeconds.value ? `${base}:${pad(d.getSeconds())}` : base
})

const dateStr = computed(() => {
  const d = now.value
  return `${d.getFullYear()}年${pad(d.getMonth() + 1)}月${pad(d.getDate())}日 周${WEEK[d.getDay()]}`
})

const chars = computed(() => timeStr.value.split(''))
function isDigit(ch: string): boolean {
  return /\d/.test(ch)
}

// 模拟时钟指针角度
const hourAngle = computed(() => (now.value.getHours() % 12) * 30 + now.value.getMinutes() * 0.5)
const minuteAngle = computed(() => now.value.getMinutes() * 6 + now.value.getSeconds() * 0.1)
const secondAngle = computed(() => now.value.getSeconds() * 6)
const ticks = Array.from({ length: 12 }, (_, i) => i * 30)
</script>

<template>
  <div class="clock-panel">
    <!-- 数字时钟 -->
    <template v-if="style === 'digital'">
      <div class="clock-digital">{{ timeStr }}</div>
      <div v-if="showDate" class="clock-date">{{ dateStr }}</div>
    </template>

    <!-- 翻牌时钟 -->
    <template v-else-if="style === 'flip'">
      <div class="clock-flip">
        <span v-for="(ch, i) in chars" :key="i" :class="isDigit(ch) ? 'cf-digit' : 'cf-sep'">
          {{ ch }}
        </span>
      </div>
      <div v-if="showDate" class="clock-date">{{ dateStr }}</div>
    </template>

    <!-- 日期时间 -->
    <template v-else-if="style === 'datetime'">
      <div v-if="showDate" class="clock-date-lg">{{ dateStr }}</div>
      <div class="clock-digital">{{ timeStr }}</div>
    </template>

    <!-- 模拟时钟 -->
    <template v-else>
      <svg class="clock-analog" viewBox="0 0 100 100">
        <circle cx="50" cy="50" r="46" fill="#fff" stroke="#e8e8e8" stroke-width="2" />
        <line
          v-for="t in ticks"
          :key="t"
          x1="50"
          y1="8"
          x2="50"
          y2="13"
          stroke="#d9d9d9"
          stroke-width="1.5"
          :transform="`rotate(${t} 50 50)`"
        />
        <line x1="50" y1="50" x2="50" y2="28" stroke="#262626" stroke-width="3" stroke-linecap="round" :transform="`rotate(${hourAngle} 50 50)`" />
        <line x1="50" y1="50" x2="50" y2="18" stroke="#262626" stroke-width="2" stroke-linecap="round" :transform="`rotate(${minuteAngle} 50 50)`" />
        <line v-if="showSeconds" x1="50" y1="54" x2="50" y2="14" stroke="#1677ff" stroke-width="1" stroke-linecap="round" :transform="`rotate(${secondAngle} 50 50)`" />
        <circle cx="50" cy="50" r="2.5" fill="#262626" />
      </svg>
    </template>
  </div>
</template>

<style scoped lang="scss">
.clock-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
}

.clock-digital {
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 34px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.1;
}

.clock-date {
  font-size: 13px;
  color: #999;
}

.clock-date-lg {
  font-size: 15px;
  color: #595959;
}

.clock-flip {
  display: flex;
  align-items: center;
  gap: 4px;
}

.cf-digit {
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

.cf-sep {
  color: #262626;
  font-size: 24px;
  font-weight: 700;
}

.clock-analog {
  width: 100%;
  height: 100%;
  max-width: 160px;
  max-height: 160px;
}
</style>

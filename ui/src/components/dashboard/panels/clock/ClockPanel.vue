<script setup lang="ts">
/**
 * 时钟面板：实时时钟，支持数字/翻牌/模拟/日期时间多种样式。无数据集依赖。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import FlipDigit from './FlipDigit.vue'
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
// 表盘刻度：60 分钟刻度（去除整点）+ 12 整点粗刻度
const minuteTicks = Array.from({ length: 60 }, (_, i) => i * 6).filter((d) => d % 30 !== 0)
const hourTicks = Array.from({ length: 12 }, (_, i) => i * 30)
// 四个方位数字时标
const cardinals = [
  { t: '12', x: 50, y: 16 },
  { t: '3', x: 84, y: 50 },
  { t: '6', x: 50, y: 84 },
  { t: '9', x: 16, y: 50 },
]
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
        <template v-for="(ch, i) in chars" :key="i">
          <FlipDigit v-if="isDigit(ch)" :value="ch" />
          <span v-else class="cf-sep">{{ ch }}</span>
        </template>
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
        <circle cx="50" cy="50" r="47" fill="#fff" stroke="#eceef1" stroke-width="1.5" />
        <circle cx="50" cy="50" r="43" fill="none" stroke="#f5f6f7" stroke-width="1" />
        <!-- 分钟细刻度 -->
        <line
          v-for="m in minuteTicks"
          :key="'m' + m"
          x1="50"
          y1="7"
          x2="50"
          y2="10"
          stroke="#e2e5ea"
          stroke-width="0.8"
          :transform="`rotate(${m} 50 50)`"
        />
        <!-- 整点粗刻度 -->
        <line
          v-for="h in hourTicks"
          :key="'h' + h"
          x1="50"
          y1="7"
          x2="50"
          y2="12"
          stroke="#c2c7cf"
          stroke-width="1.8"
          stroke-linecap="round"
          :transform="`rotate(${h} 50 50)`"
        />
        <!-- 方位数字 -->
        <text
          v-for="c in cardinals"
          :key="c.t"
          :x="c.x"
          :y="c.y"
          text-anchor="middle"
          dominant-baseline="central"
          font-size="8"
          font-weight="600"
          fill="#8c8c8c"
        >{{ c.t }}</text>
        <!-- 时针（带尾） -->
        <line x1="50" y1="55" x2="50" y2="31" stroke="#262626" stroke-width="3.4" stroke-linecap="round" :transform="`rotate(${hourAngle} 50 50)`" />
        <!-- 分针（带尾） -->
        <line x1="50" y1="57" x2="50" y2="19" stroke="#262626" stroke-width="2.4" stroke-linecap="round" :transform="`rotate(${minuteAngle} 50 50)`" />
        <!-- 秒针 + 尾配重 -->
        <g v-if="showSeconds" :transform="`rotate(${secondAngle} 50 50)`">
          <line x1="50" y1="60" x2="50" y2="14" stroke="#1677ff" stroke-width="1" stroke-linecap="round" />
          <circle cx="50" cy="60" r="2" fill="#1677ff" />
        </g>
        <!-- 中心轴帽 -->
        <circle cx="50" cy="50" r="3.4" fill="#262626" />
        <circle cx="50" cy="50" r="1.4" fill="#fff" />
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
  font-size: var(--dash-text-size, 34px);
  font-weight: var(--dash-text-weight, 700);
  color: var(--dash-text-color, #1a1a1a);
  line-height: 1.1;
}

.clock-date {
  font-size: 13px;
  color: #999;
}

.clock-date-lg {
  font-size: 15px;
  color: var(--dash-text-color, #595959);
}

.clock-flip {
  display: flex;
  align-items: center;
  gap: 5px;
  perspective: 320px;
}

.cf-sep {
  color: #595959;
  font-size: 28px;
  font-weight: 700;
  padding: 0 1px;
}

.clock-analog {
  width: 100%;
  height: 100%;
  max-width: 160px;
  max-height: 160px;
}
</style>

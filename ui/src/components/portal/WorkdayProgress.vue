<script setup lang="ts">
/**
 * 摸鱼进度条：工作日进度 + 阶段文案。
 * 百分比数字滚动（一次性缓动）、25/50/75 里程碑点亮、阶段文案切换过渡。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { animateNumber } from './_shared/animate'

defineOptions({
  portalMeta: {
    name: '摸鱼进度条',
    description: '今天上了多久班、离下班还有多远，阶段文案陪你熬到下班',
  },
})

const props = withDefaults(
  defineProps<{
    /** 上班时间 HH:mm */
    workStart?: string
    /** 下班时间 HH:mm */
    workEnd?: string
    /** 自定义口号（显示在底部） */
    slogan?: string
  }>(),
  { workStart: '09:00', workEnd: '18:00', slogan: '' },
)

const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 60 * 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  cancelCount?.()
})

function refresh() {
  now.value = new Date()
}
defineExpose({ refresh })

/** "HH:mm" 转当日分钟数，非法输入回退默认 */
function toMinutes(text: string, fallback: number): number {
  const m = /^(\d{1,2}):(\d{2})$/.exec(text.trim())
  if (!m) return fallback
  const h = Number(m[1])
  const min = Number(m[2])
  if (h > 23 || min > 59) return fallback
  return h * 60 + min
}

const startMin = computed(() => toMinutes(props.workStart, 9 * 60))
const endMin = computed(() => toMinutes(props.workEnd, 18 * 60))
const nowMin = computed(() => now.value.getHours() * 60 + now.value.getMinutes())

/** 工作进度 0-1；上班前 <0、下班后 >1 */
const rawRatio = computed(() => {
  const total = endMin.value - startMin.value
  if (total <= 0) return 1
  return (nowMin.value - startMin.value) / total
})
const percent = computed(() => Math.round(Math.min(1, Math.max(0, rawRatio.value)) * 100))

// 百分比数字滚动（一次性缓动，进度变化时从旧值滚到新值）
const displayPercent = ref(0)
let cancelCount: (() => void) | null = null
watch(
  percent,
  (to, from) => {
    cancelCount?.()
    cancelCount = animateNumber(from ?? 0, to, 800, (v) => {
      displayPercent.value = Math.round(v)
    })
  },
  { immediate: true },
)

const MILESTONES = [25, 50, 75]

const phase = computed(() => {
  const r = rawRatio.value
  if (r < 0) return '还没上班，再眯一会'
  if (r >= 1) return '已下班，别卷了'
  if (r < 0.25) return '刚上班，先喝口水'
  if (r < 0.5) return '渐入佳境，摸鱼要适度'
  if (r < 0.75) return '已过半，稳住'
  return '冲刺！马上下班'
})

function fmt(mins: number): string {
  const m = Math.max(0, mins)
  return `${Math.floor(m / 60)}h ${m % 60}m`
}

const detail = computed(() => {
  const r = rawRatio.value
  if (r < 0) return `距上班还有 ${fmt(startMin.value - nowMin.value)}`
  if (r >= 1) return `今日已完成 ${fmt(endMin.value - startMin.value)} 工作时长`
  return `已上班 ${fmt(nowMin.value - startMin.value)} · 距下班 ${fmt(endMin.value - nowMin.value)}`
})
</script>

<template>
  <div class="workday">
    <div class="wd-head">
      <Transition name="wd-phase" mode="out-in">
        <span :key="phase" class="wd-phase">{{ phase }}</span>
      </Transition>
      <span class="wd-num">{{ displayPercent }}<i class="wd-num-unit">%</i></span>
    </div>
    <div class="wd-bar">
      <div class="wd-fill" :style="{ width: percent + '%' }" />
      <span
        v-for="m in MILESTONES"
        :key="m"
        class="wd-dot"
        :class="{ passed: percent >= m }"
        :style="{ left: m + '%' }"
        :title="m + '%'"
      />
    </div>
    <div class="wd-detail">{{ detail }}</div>
    <div v-if="slogan" class="wd-slogan">{{ slogan }}</div>
  </div>
</template>

<style scoped lang="scss">
.workday {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  height: 100%;
}

.wd-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.wd-phase {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

/* 阶段文案切换：一次性上移淡入 */
.wd-phase-enter-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.wd-phase-leave-active {
  transition: opacity 0.2s ease;
}

.wd-phase-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.wd-phase-leave-to {
  opacity: 0;
}

.wd-num {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #1677ff;
  line-height: 1;
}

.wd-num-unit {
  margin-left: 1px;
  font-size: 13px;
  font-style: normal;
  font-weight: 600;
  color: #8c8c8c;
}

.wd-bar {
  position: relative;
  height: 12px;
  border-radius: 6px;
  background: #f0f2f5;
}

.wd-fill {
  height: 100%;
  border-radius: 6px;
  background: #1677ff;
  transition: width 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}

/* 里程碑节点：经过后点亮为白心蓝圈 */
.wd-dot {
  position: absolute;
  top: 50%;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid #d9dde3;
  background: #fff;
  transform: translate(-50%, -50%);
  transition: border-color 0.4s ease;
}

.wd-dot.passed {
  border-color: #1677ff;
}

.wd-detail {
  font-size: 13px;
  color: #8c8c8c;
}

.wd-slogan {
  font-size: 12px;
  color: #bfbfbf;
}
</style>

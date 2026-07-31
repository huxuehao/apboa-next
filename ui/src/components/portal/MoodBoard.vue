<script setup lang="ts">
/**
 * 心情打卡：自绘 SVG 线条表情记录今日心情，展示最近一周与连续天数。
 * localStorage 按面板持久化，保留最近 30 天。
 *
 * @author huxuehao
 */
import { computed, ref } from 'vue'
import dayjs from 'dayjs'

defineOptions({
  portalMeta: {
    name: '心情打卡',
    description: '每天记录一次心情，最近一周一眼看见自己的状态',
  },
})

const props = withDefaults(
  defineProps<{
    /** 5 档心情文案（依次对应：开心/平静/一般/疲惫/低落） */
    labels?: string[]
    panelContext?: { panelId: string; title: string; interactive: boolean }
  }>(),
  { labels: () => [], panelContext: undefined },
)

interface MoodDef {
  key: string
  label: string
  color: string
  /** 嘴部路径（自绘 SVG，24x24 视口） */
  mouth: string
  /** 眼睛是否闭合（疲惫态用横线眼） */
  tired?: boolean
}

const MOODS: MoodDef[] = [
  { key: 'happy', label: '开心', color: '#389e0d', mouth: 'M8 14c1.2 1.8 2.6 2.6 4 2.6s2.8-.8 4-2.6' },
  { key: 'calm', label: '平静', color: '#1677ff', mouth: 'M8.5 15.2c1.1.8 2.3 1.2 3.5 1.2s2.4-.4 3.5-1.2' },
  { key: 'meh', label: '一般', color: '#8c8c8c', mouth: 'M8.5 15.5h7' },
  { key: 'tired', label: '疲惫', color: '#d46b08', mouth: 'M8.5 16c1.1-.6 2.3-.9 3.5-.9s2.4.3 3.5.9', tired: true },
  { key: 'down', label: '低落', color: '#722ed1', mouth: 'M8 16.6c1.2-1.8 2.6-2.6 4-2.6s2.8.8 4 2.6' },
]

const moodLabel = (idx: number) => props.labels[idx] || MOODS[idx]?.label || ''

function storageKey(): string {
  return `apboa-mood:${props.panelContext?.panelId || 'default'}`
}

type MoodHistory = Record<string, string>

function loadHistory(): MoodHistory {
  try {
    const raw = localStorage.getItem(storageKey())
    const parsed = raw ? JSON.parse(raw) : {}
    return parsed && typeof parsed === 'object' ? (parsed as MoodHistory) : {}
  } catch {
    return {}
  }
}

const history = ref<MoodHistory>(loadHistory())

function saveHistory() {
  try {
    // 仅保留最近 30 天，避免无限膨胀
    const cutoff = dayjs().subtract(30, 'day').format('YYYY-MM-DD')
    const next: MoodHistory = {}
    Object.entries(history.value).forEach(([day, mood]) => {
      if (day >= cutoff) next[day] = mood
    })
    history.value = next
    localStorage.setItem(storageKey(), JSON.stringify(next))
  } catch {
    // 存储不可用时静默降级为会话内记录
  }
}

const today = computed(() => dayjs().format('YYYY-MM-DD'))
const todayMood = computed(() => history.value[today.value] || '')

function pickMood(key: string) {
  history.value = { ...history.value, [today.value]: key }
  saveHistory()
}

/** 心情能量等级（越开心越高），驱动周视图迷你柱高度 */
const LEVELS: Record<string, number> = { happy: 5, calm: 4, meh: 3, tired: 2, down: 1 }

/** 最近 7 天（含今日），用于心情迷你柱状图 */
const recentDays = computed(() =>
  Array.from({ length: 7 }, (_, i) => {
    const day = dayjs().subtract(6 - i, 'day').format('YYYY-MM-DD')
    const mood = MOODS.find((m) => m.key === history.value[day])
    return {
      day,
      color: mood?.color || '#e8eaed',
      label: mood ? mood.label : '未记录',
      level: mood ? LEVELS[mood.key] || 3 : 0,
      isToday: i === 6,
    }
  }),
)

/** 以今日为终点的连续打卡天数 */
const streak = computed(() => {
  let n = 0
  for (let i = 0; ; i++) {
    const day = dayjs().subtract(i, 'day').format('YYYY-MM-DD')
    if (history.value[day]) n++
    else break
  }
  return n
})

function refresh() {
  history.value = loadHistory()
}
defineExpose({ refresh })
</script>

<template>
  <div class="mood">
    <div class="md-faces">
      <button
        v-for="(m, idx) in MOODS"
        :key="m.key"
        class="md-face"
        :class="{ active: todayMood === m.key }"
        :style="todayMood === m.key ? { borderColor: m.color } : {}"
        :title="moodLabel(idx)"
        @click="pickMood(m.key)"
      >
        <svg viewBox="0 0 24 24" class="md-svg" :style="{ color: todayMood === m.key ? m.color : '#8c8c8c' }">
          <circle cx="12" cy="12" r="9.2" fill="none" stroke="currentColor" stroke-width="1.5" />
          <template v-if="m.tired">
            <path d="M7.6 10h3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            <path d="M13.4 10h3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </template>
          <template v-else>
            <circle cx="9" cy="10" r="1.1" fill="currentColor" />
            <circle cx="15" cy="10" r="1.1" fill="currentColor" />
          </template>
          <path :d="m.mouth" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
        <span class="md-label">{{ moodLabel(idx) }}</span>
      </button>
    </div>
    <div class="md-week">
      <div
        v-for="(d, i) in recentDays"
        :key="d.day"
        class="md-day"
        :class="{ today: d.isToday }"
        :title="`${d.day} ${d.label}`"
      >
        <span
          class="md-bar"
          :style="{
            height: (d.level ? d.level * 5 + 6 : 5) + 'px',
            background: d.color,
            animationDelay: i * 0.06 + 's',
          }"
        />
      </div>
    </div>
    <Transition name="md-fade" mode="out-in">
      <div :key="streak >= 3 ? 's' + streak : todayMood ? 'done' : 'ask'" class="md-streak">
        <template v-if="streak >= 3">已连续记录 {{ streak }} 天</template>
        <template v-else-if="todayMood">今日已记录</template>
        <template v-else>今天感觉怎么样？</template>
      </div>
    </Transition>
  </div>
</template>

<style scoped lang="scss">
.mood {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
}

.md-faces {
  display: flex;
  gap: 8px;
}

.md-face {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
}

.md-face.active {
  background: #fafafa;
}

/* 选中表情：一次性弹性强调（选择变化时重放） */
.md-face.active .md-svg {
  animation: md-pop 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes md-pop {
  from {
    transform: scale(0.72);
  }
  to {
    transform: scale(1);
  }
}

.md-svg {
  width: 26px;
  height: 26px;
}

.md-label {
  font-size: 11px;
  color: #8c8c8c;
}

.md-week {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 34px;
}

.md-day {
  display: flex;
  align-items: flex-end;
  width: 14px;
  height: 100%;
}

.md-day.today .md-bar {
  outline: 1px solid #d9dde3;
  outline-offset: 1px;
}

/* 迷你柱：自底部长出的一次性错落入场 */
.md-bar {
  width: 100%;
  border-radius: 3px;
  transform-origin: center bottom;
  animation: md-grow 0.4s ease-out both;
}

@keyframes md-grow {
  from {
    transform: scaleY(0);
  }
  to {
    transform: scaleY(1);
  }
}

.md-streak {
  font-size: 12px;
  color: #999;
}

.md-fade-enter-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.md-fade-leave-active {
  transition: opacity 0.15s ease;
}

.md-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.md-fade-leave-to {
  opacity: 0;
}
</style>

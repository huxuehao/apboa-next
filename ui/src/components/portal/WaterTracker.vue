<script setup lang="ts">
/**
 * 今日饮水打卡：点杯子记录饮水，localStorage 按面板与日期持久化，跨天自动清零。
 * 水位在杯内上涨（一次性过渡），连点多杯错落上水，达标庆祝文案弹出。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'

defineOptions({
  portalMeta: {
    name: '饮水打卡',
    description: '一天八杯水打卡，杯中水位随点击上涨，照顾好自己',
  },
})

const props = withDefaults(
  defineProps<{
    /** 目标杯数 */
    goal?: number
    /** 每杯毫升数（用于统计） */
    cupMl?: number
    panelContext?: { panelId: string; title: string; interactive: boolean }
  }>(),
  { goal: 8, cupMl: 250, panelContext: undefined },
)

const goalCount = computed(() => Math.min(12, Math.max(1, Math.round(props.goal))))

/** clipPath id 需全局唯一（同页可能有多个实例） */
const uid = Math.random().toString(36).slice(2, 8)

function todayKey(): string {
  const day = new Date().toISOString().slice(0, 10)
  return `apboa-water:${props.panelContext?.panelId || 'default'}:${day}`
}

function loadCount(): number {
  try {
    const raw = localStorage.getItem(todayKey())
    const n = raw === null ? 0 : Number(raw)
    return Number.isFinite(n) ? Math.max(0, Math.min(12, n)) : 0
  } catch {
    return 0
  }
}

const count = ref(loadCount())
/** 上一次的杯数：用于计算错落上水延迟与顶杯弹跳 */
const lastFrom = ref(count.value)

watch(count, (v, old) => {
  lastFrom.value = old
  try {
    localStorage.setItem(todayKey(), String(v))
  } catch {
    // 存储不可用时静默降级为会话内计数
  }
})

/** 点击杯子：点未点亮的杯子填充至该位置，点最后一个已点亮的杯子撤销一杯 */
function tapCup(index: number) {
  count.value = index + 1 === count.value ? index : index + 1
}

function reset() {
  count.value = 0
}

/** 刷新时跨天则重载（日期 key 变化后读取为 0） */
function refresh() {
  count.value = loadCount()
}

defineExpose({
  panelActions: [{ key: 'reset', label: '重置', icon: 'DeleteOutlined', run: reset }],
  refresh,
})

const done = computed(() => count.value >= goalCount.value)
const mlText = computed(() => `约 ${count.value * Math.max(1, props.cupMl)} ml`)

/** 批量补杯时的错落上水延迟（秒） */
function riseDelay(index: number): string {
  if (index < count.value && index >= lastFrom.value) {
    return (index - lastFrom.value) * 0.08 + 's'
  }
  return '0s'
}
</script>

<template>
  <div class="water">
    <div class="wt-title">
      <span v-if="done" class="wt-done-text">今日水分达标，干得漂亮</span>
      <span v-else>今日 {{ count }} / {{ goalCount }} 杯 <i class="wt-ml">{{ mlText }}</i></span>
    </div>
    <div class="wt-cups">
      <button
        v-for="i in goalCount"
        :key="i"
        class="wt-cup"
        :class="{ pop: i - 1 === count - 1 && count > lastFrom }"
        :title="i <= count ? '点击撤销' : '喝一杯'"
        @click="tapCup(i - 1)"
      >
        <svg viewBox="0 0 24 26" class="wt-cup-svg">
          <defs>
            <clipPath :id="`wt-${uid}-${i}`">
              <path d="M6.6 4h10.8l-1.3 15.5a1.8 1.8 0 0 1-1.8 1.6h-4.6a1.8 1.8 0 0 1-1.8-1.6L6.6 4z" />
            </clipPath>
          </defs>
          <!-- 水体：杯形裁剪内自底部上涨（transform 过渡，一次性） -->
          <g :clip-path="`url(#wt-${uid}-${i})`">
            <rect
              x="4"
              y="4"
              width="16"
              height="18"
              fill="#1677ff"
              class="wt-water"
              :class="{ filled: i <= count }"
              :style="{ transitionDelay: riseDelay(i - 1) }"
            />
          </g>
          <path
            d="M6.6 4h10.8l-1.3 15.5a1.8 1.8 0 0 1-1.8 1.6h-4.6a1.8 1.8 0 0 1-1.8-1.6L6.6 4z"
            fill="none"
            :stroke="i <= count ? '#1677ff' : '#c9ced6'"
            stroke-width="1.5"
            stroke-linejoin="round"
            class="wt-outline"
          />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.water {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
}

.wt-title {
  font-size: 14px;
  color: #595959;
}

.wt-ml {
  margin-left: 6px;
  font-size: 12px;
  font-style: normal;
  color: #bfbfbf;
}

/* 达标文案：数据变化触发的一次性强调动画 */
.wt-done-text {
  display: inline-block;
  font-weight: 600;
  color: #389e0d;
  animation: wt-pop 0.35s ease-out;
}

@keyframes wt-pop {
  from {
    transform: scale(0.7);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

.wt-cups {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.wt-cup {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 40px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

/* 新点亮的杯子：一次性轻弹强调 */
.wt-cup.pop .wt-cup-svg {
  animation: wt-cup-pop 0.3s ease-out;
}

@keyframes wt-cup-pop {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.14);
  }
  100% {
    transform: scale(1);
  }
}

.wt-cup-svg {
  width: 26px;
  height: 30px;
}

/* 水位：自底部上涨的一次性过渡 */
.wt-water {
  transform: scaleY(0);
  transform-origin: center bottom;
  transform-box: fill-box;
  transition: transform 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}

.wt-water.filled {
  transform: scaleY(1);
}

.wt-outline {
  transition: stroke 0.3s ease;
}
</style>

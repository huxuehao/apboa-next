<script setup lang="ts">
/**
 * 倒数日：未来事件显示"还有 N 天"，过去事件自动变"已坚持 N 天"。
 * 大数字从 0 滚动到目标值（一次性缓动），临近/坚持有情绪化副文案。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { animateNumber } from './_shared/animate'

defineOptions({
  portalMeta: {
    name: '倒数日',
    description: '纪念日与目标日倒计时，把期待和坚持变成看得见的大数字',
  },
})

const props = withDefaults(
  defineProps<{
    /** 事件名称 */
    title?: string
    /** 目标日期：YYYY-MM-DD 字符串或 Date 对象（props 日期类型会注入 Date） */
    date?: string | Date
    /** 数字主色 */
    color?: string
  }>(),
  { title: '目标日', date: '', color: '#1677ff' },
)

const tick = ref(0)
function refresh() {
  tick.value += 1
}
defineExpose({ refresh })

const target = computed(() => {
  void tick.value
  // Date 对象直接用；字符串用正则预校验格式（不依赖 customParseFormat 插件）
  if (props.date instanceof Date) {
    const d = dayjs(props.date)
    return d.isValid() ? d.startOf('day') : null
  }
  const text = props.date.trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return null
  const d = dayjs(text)
  return d.isValid() ? d.startOf('day') : null
})

const diffDays = computed(() => {
  void tick.value
  if (!target.value) return null
  return target.value.diff(dayjs().startOf('day'), 'day')
})

const isFuture = computed(() => (diffDays.value ?? 0) >= 0)
const days = computed(() => Math.abs(diffDays.value ?? 0))
const isToday = computed(() => diffDays.value === 0)
const prefix = computed(() => (isFuture.value ? '还有' : '已坚持'))

// 大数字滚动：目标值变化时从 0 滚到 N（一次性）
const displayDays = ref(0)
let cancelCount: (() => void) | null = null
watch(
  days,
  (to) => {
    cancelCount?.()
    cancelCount = animateNumber(0, to, Math.min(1200, 400 + to * 8), (v) => {
      displayDays.value = Math.round(v)
    })
  },
  { immediate: true },
)
onBeforeUnmount(() => cancelCount?.())

/** 情绪化副文案：临近提醒 / 长期坚持鼓励 */
const emotion = computed(() => {
  if (!target.value || isToday.value) return ''
  if (isFuture.value && days.value <= 3) return '就快到了'
  if (!isFuture.value && days.value >= 100) return '了不起的坚持'
  return ''
})
</script>

<template>
  <div class="countdown">
    <template v-if="target">
      <div class="cd-title">{{ title }}</div>
      <div v-if="isToday" class="cd-today" :style="{ color }">就是今天</div>
      <div v-else class="cd-main">
        <span class="cd-prefix">{{ prefix }}</span>
        <span class="cd-num" :style="{ color }">{{ displayDays }}</span>
        <span class="cd-unit">天</span>
      </div>
      <Transition name="cd-fade">
        <div v-if="emotion" class="cd-emotion">{{ emotion }}</div>
      </Transition>
      <div class="cd-date">{{ target.format('YYYY年MM月DD日') }}</div>
    </template>
    <div v-else class="cd-empty">在组件 props 配置 date（YYYY-MM-DD）与 title</div>
  </div>
</template>

<style scoped lang="scss">
.countdown {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 100%;
}

.cd-title {
  font-size: 14px;
  font-weight: 600;
  color: #434343;
}

.cd-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.cd-prefix,
.cd-unit {
  font-size: 14px;
  color: #8c8c8c;
}

.cd-num {
  font-size: 48px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

/* 目标日当天：一次性放大入场 */
.cd-today {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.2;
  animation: cd-pop 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes cd-pop {
  from {
    transform: scale(0.6);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

.cd-emotion {
  font-size: 12px;
  font-weight: 600;
  color: #d46b08;
}

.cd-fade-enter-active {
  transition: opacity 0.5s ease 0.5s;
}

.cd-fade-enter-from {
  opacity: 0;
}

.cd-date {
  padding: 2px 10px;
  border-radius: 10px;
  background: #f5f6f8;
  font-size: 12px;
  color: #8c8c8c;
}

.cd-empty {
  font-size: 13px;
  color: #bbb;
  text-align: center;
  padding: 0 12px;
}
</style>

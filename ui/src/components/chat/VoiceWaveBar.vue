/**
 * 录音实时波浪条：竖条滚动展示麦克风音量，让用户对「系统听到了什么」有实时反馈
 * （说话有浪、安静无浪，麦克风异常当场可见——比事后的静音拒识更前置的防错）
 *
 * @component
 */
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 实时音量 0~1（来自 pcmRecorder 的 onLevel） */
    level: number
    /** 竖条数量：桌面 44（约 260px），移动端建议 18 左右（窄屏放得下计时与提示） */
    count?: number
  }>(),
  { count: 44 }
)

/** 波形滚动步进间隔（ms） */
const STEP_MS = 90
/** 静音时的基线高度（保持波形条可见） */
const MIN_BAR = 0.08

const bars = ref<number[]>(Array(props.count).fill(MIN_BAR))
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    const value = Math.max(MIN_BAR, Math.min(1, props.level))
    bars.value = [...bars.value.slice(1), value]
  }, STEP_MS)
})

onBeforeUnmount(() => {
  if (timer !== null) {
    clearInterval(timer)
  }
})
</script>

<template>
  <div class="voice-wave" aria-hidden="true">
    <span
      v-for="(bar, index) in bars"
      :key="index"
      class="voice-wave-bar"
      :style="{ height: `${Math.round(bar * 100)}%` }"
    />
  </div>
</template>

<style scoped lang="scss">
.voice-wave {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 28px;
}

.voice-wave-bar {
  width: 3px;
  min-height: 3px;
  border-radius: 2px;
  background-color: #ff4d4f;
  transition: height 80ms linear;
}
</style>

/**
 * 移动端「按住说话」条：语音模式下替换编辑器区域。
 * 触摸手势状态机收敛在本组件（与按钮 DOM 位置强相关），只向上抛语义化动作，
 * 录音原语（start/stop/cancel）由 Chat 页统一映射到 useVoiceInput。
 *
 * 手势规格：touchstart 立即录音（按钮语义唯一，无需长按判定，超短误触由
 * 状态机 <800ms 丢弃兜底）；touchmove 上滑越过按钮上缘 50px 进入取消预备
 * （UI 变红）；touchend 按预备态决定发送或丢弃；touchcancel（来电/系统打断）
 * 一律取消；只跟踪第一根手指。
 *
 * @component
 */
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import VoiceWaveBar from './VoiceWaveBar.vue'
import type { VoiceInputState } from '@/composables/chat/useVoiceInput'

const props = defineProps<{
  voiceState?: VoiceInputState
  /** AI 回复中禁用录音（置灰提示，touchstart 忽略） */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'press', action: 'start' | 'end' | 'cancel' | 'intent-on' | 'intent-off'): void
}>()

const rootRef = ref<HTMLElement | null>(null)

/** 上滑取消阈值：手指高于按钮上缘此距离（px）进入取消预备 */
const CANCEL_OFFSET = 50

/** 当前跟踪的手指 identifier（null=未按压；忽略多点触摸的后续手指） */
let touchId: number | null = null
let intentOn = false

const isRecording = computed(
  () => props.voiceState?.status === 'recording' && props.voiceState?.mode === 'press'
)
const isCancelIntent = computed(() => !!props.voiceState?.cancelIntent)

/** 录音计时（m:ss） */
const timeText = computed(() => {
  const total = props.voiceState?.seconds ?? 0
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
})

function findTrackedTouch(e: TouchEvent): Touch | null {
  for (let i = 0; i < e.changedTouches.length; i++) {
    const touch = e.changedTouches.item(i)
    if (touch && touch.identifier === touchId) return touch
  }
  return null
}

/** 被跟踪的手指是否仍留在屏幕上（e.touches 为当前所有在屏触点） */
function trackedTouchStillDown(e: TouchEvent): boolean {
  for (let i = 0; i < e.touches.length; i++) {
    if (e.touches.item(i)?.identifier === touchId) return true
  }
  return false
}

function onTouchStart(e: TouchEvent) {
  if (props.disabled) return
  if (touchId !== null) return
  const touch = e.changedTouches.item(0)
  if (!touch) return
  // 阻止长按呼出 iOS 放大镜/文本选择与页面滚动（配合 CSS touch-action: none）
  e.preventDefault()
  touchId = touch.identifier
  intentOn = false
  emit('press', 'start')
}

function onTouchMove(e: TouchEvent) {
  if (touchId === null) return
  const touch = findTrackedTouch(e)
  if (!touch) return
  e.preventDefault()
  const rect = rootRef.value?.getBoundingClientRect()
  if (!rect) return
  const out = touch.clientY < rect.top - CANCEL_OFFSET
  if (out !== intentOn) {
    intentOn = out
    emit('press', out ? 'intent-on' : 'intent-off')
  }
}

function onTouchEnd(e: TouchEvent) {
  if (touchId === null) return
  // 自愈判定：只要被跟踪的手指已不在屏上就走结束流程（不依赖 changedTouches
  // 是否包含它），任何原因的事件丢失都不会让触摸层永久死锁
  if (trackedTouchStillDown(e)) return
  e.preventDefault()
  touchId = null
  emit('press', intentOn ? 'cancel' : 'end')
  intentOn = false
}

function onTouchCancel() {
  if (touchId === null) return
  touchId = null
  intentOn = false
  emit('press', 'cancel')
}

onBeforeUnmount(() => {
  touchId = null
})
</script>

<template>
  <div
    ref="rootRef"
    class="voice-press-bar"
    :class="{ 'is-recording': isRecording, 'is-cancel-intent': isCancelIntent, 'is-disabled': disabled }"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchCancel"
  >
    <span v-if="!isRecording" class="press-label">{{ disabled ? 'AI 回复中，请稍候…' : '按住说话' }}</span>
    <template v-else>
      <span class="press-time">{{ timeText }}</span>
      <VoiceWaveBar :level="voiceState?.level ?? 0" :count="18" />
      <span class="press-hint">{{ isCancelIntent ? '松开手指，取消发送' : '松开发送 · 上滑取消' }}</span>
    </template>
  </div>
</template>

<style scoped lang="scss">
.voice-press-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 60px;
  padding: 5px 0;
  border-radius: var(--border-radius-md);
  background-color: var(--color-bg-light, #f5f5f5);
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
  font-weight: 500;
  user-select: none;
  -webkit-user-select: none;
  /* 触摸期间禁止浏览器接管滚动/缩放手势 */
  touch-action: none;

  /* 触摸序列的事件目标锁定为 touchstart 时手指下的最深元素：若目标是内部
     子节点（如「按住说话」文字），录音开始后被 v-if 移除，后续 touchmove/
     touchend 派发给已分离节点、冒泡链断裂，滑动取消与松手全部失灵。
     让所有后代不接收指针事件，触摸目标恒为本容器（全程不卸载）。 */
  * {
    pointer-events: none;
  }

  &.is-recording {
    background-color: #fff1f0;
  }

  &.is-disabled {
    opacity: 0.55;

    .press-label {
      color: var(--color-text-placeholder);
    }
  }

  &.is-cancel-intent {
    background-color: #ff4d4f;

    .press-time,
    .press-hint {
      color: #fff;
    }

    :deep(.voice-wave-bar) {
      background-color: #fff;
    }
  }
}

.press-label {
  color: var(--color-text-secondary);
}

.press-time {
  font-size: 13px;
  color: #ff4d4f;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.press-hint {
  font-size: 12px;
  color: var(--color-text-placeholder);
  flex-shrink: 0;
}
</style>

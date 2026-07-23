/**
 * 头像裁切弹窗
 *
 * 微信式交互：固定方形裁切窗，拖动图片调整构图、滑杆/滚轮缩放，
 * 确认后按当前构图输出 128x128 base64（PNG 保留透明，其余 JPEG）。
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'

/** 裁切舞台边长（即裁切窗口，所见即所裁；需与样式 .avatar-crop-stage 尺寸一致） */
const STAGE = 440
/** 输出边长（512 兼顾全屏预览清晰度与 base64 体积，DB 列为 MEDIUMTEXT） */
const OUTPUT = 512
const MIN_ZOOM = 1
const MAX_ZOOM = 3

const props = defineProps<{
  visible: boolean
  /** 待裁切图片地址（objectURL） */
  imageUrl: string
  /** 源图是否 PNG（输出保留透明通道） */
  isPng?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', dataUrl: string): void
}>()

const imgRef = ref<HTMLImageElement | null>(null)
const naturalW = ref(0)
const naturalH = ref(0)
const zoom = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)

/** cover 铺满舞台的基础缩放（短边贴合窗口） */
const baseScale = computed(() => {
  if (!naturalW.value || !naturalH.value) return 1
  return STAGE / Math.min(naturalW.value, naturalH.value)
})

/** 当前显示缩放（显示像素 / 原图像素） */
const displayScale = computed(() => baseScale.value * zoom.value)

const imgStyle = computed(() => {
  const w = naturalW.value * displayScale.value
  const h = naturalH.value * displayScale.value
  return {
    width: `${w}px`,
    height: `${h}px`,
    left: `${STAGE / 2 + offsetX.value - w / 2}px`,
    top: `${STAGE / 2 + offsetY.value - h / 2}px`
  }
})

/**
 * 把偏移限制在图片始终完整覆盖裁切窗的范围内
 */
function clampOffset() {
  const maxX = Math.max(0, (naturalW.value * displayScale.value - STAGE) / 2)
  const maxY = Math.max(0, (naturalH.value * displayScale.value - STAGE) / 2)
  offsetX.value = Math.min(maxX, Math.max(-maxX, offsetX.value))
  offsetY.value = Math.min(maxY, Math.max(-maxY, offsetY.value))
}

function handleImageLoad(e: Event) {
  const img = e.target as HTMLImageElement
  naturalW.value = img.naturalWidth
  naturalH.value = img.naturalHeight
  zoom.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

// 缩放绕舞台中心：偏移随缩放比例同步调整后再夹紧
watch(zoom, (nv, ov) => {
  if (!ov) return
  const ratio = nv / ov
  offsetX.value *= ratio
  offsetY.value *= ratio
  clampOffset()
})

// 每次打开重置构图（同一图片再次打开也从初始状态开始）
watch(() => props.visible, (v) => {
  if (v && naturalW.value) {
    zoom.value = 1
    offsetX.value = 0
    offsetY.value = 0
  }
})

// ---------- 拖动 ----------
let dragging = false
let startX = 0
let startY = 0
let startOffsetX = 0
let startOffsetY = 0

function onPointerDown(e: PointerEvent) {
  dragging = true
  startX = e.clientX
  startY = e.clientY
  startOffsetX = offsetX.value
  startOffsetY = offsetY.value
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging) return
  offsetX.value = startOffsetX + (e.clientX - startX)
  offsetY.value = startOffsetY + (e.clientY - startY)
  clampOffset()
}

function onPointerUp() {
  dragging = false
}

function onWheel(e: WheelEvent) {
  e.preventDefault()
  const next = zoom.value + (e.deltaY < 0 ? 0.1 : -0.1)
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Number(next.toFixed(2))))
}

/**
 * 按当前构图裁切输出
 */
function handleConfirm() {
  const img = imgRef.value
  if (!img || !naturalW.value) return
  const canvas = document.createElement('canvas')
  canvas.width = OUTPUT
  canvas.height = OUTPUT
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  // 舞台左上角反解到原图坐标：图片中心位于舞台 (STAGE/2 + offset)
  const scale = displayScale.value
  const sx = naturalW.value / 2 - (STAGE / 2 + offsetX.value) / scale
  const sy = naturalH.value / 2 - (STAGE / 2 + offsetY.value) / scale
  const sSide = STAGE / scale
  ctx.drawImage(img, sx, sy, sSide, sSide, 0, 0, OUTPUT, OUTPUT)
  emit('confirm', props.isPng ? canvas.toDataURL('image/png') : canvas.toDataURL('image/jpeg', 0.85))
  emit('update:visible', false)
}

function handleCancel() {
  emit('update:visible', false)
}
</script>

<template>
  <AModal
    :open="visible"
    title="裁剪头像"
    :width="520"
    :mask-closable="false"
    ok-text="确定"
    cancel-text="取消"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <div class="avatar-crop-body">
      <div
        class="avatar-crop-stage"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @wheel="onWheel"
      >
        <img
          ref="imgRef"
          :src="imageUrl"
          alt="裁剪预览"
          class="avatar-crop-img"
          :style="imgStyle"
          draggable="false"
          @load="handleImageLoad"
        />
      </div>
      <div class="avatar-crop-zoom">
        <span class="avatar-crop-zoom-label">缩放</span>
        <ASlider
          v-model:value="zoom"
          :min="MIN_ZOOM"
          :max="MAX_ZOOM"
          :step="0.01"
          class="avatar-crop-zoom-slider"
        />
      </div>
      <div class="avatar-crop-tip text-placeholder text-xs">拖动图片调整位置，滚轮或滑杆缩放</div>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.avatar-crop-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-sm);
}

.avatar-crop-stage {
  position: relative;
  width: 440px;
  height: 440px;
  overflow: hidden;
  border-radius: var(--border-radius-md);
  background-color: #f0f0f0;
  cursor: grab;
  /* 阻止触摸设备上的滚动手势干扰拖拽 */
  touch-action: none;

  &:active {
    cursor: grabbing;
  }
}

.avatar-crop-img {
  position: absolute;
  max-width: none;
  user-select: none;
  pointer-events: none;
}

.avatar-crop-zoom {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
}

.avatar-crop-zoom-label {
  flex-shrink: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.avatar-crop-zoom-slider {
  flex: 1;
}

.avatar-crop-tip {
  text-align: center;
}
</style>

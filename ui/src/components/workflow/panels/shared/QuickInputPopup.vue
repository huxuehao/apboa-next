<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'

const props = defineProps<{
  visible: boolean
  items: string[]
  anchorEl: HTMLElement | null
}>()

const emit = defineEmits<{
  select: [name: string]
  close: []
}>()

const popupRef = ref<HTMLElement>()
const activeIndex = ref(0)
const position = ref<{ top: number; left: number }>({ top: 0, left: 0 })

// 定位策略：优先左侧 → 下方 → 右侧 → 上方
function computePosition() {
  if (!props.anchorEl) return
  const rect = props.anchorEl.getBoundingClientRect()
  // 根据实际渲染宽度计算，未渲染时兜底 200
  const popupWidth = popupRef.value?.offsetWidth || 200
  const popupHeight = Math.min(props.items.length * 40 + 16, 300)
  const gap = 8

  const spaceLeft = rect.left
  const spaceRight = window.innerWidth - rect.right
  const spaceBottom = window.innerHeight - rect.bottom

  let top = 0
  let left = 0

  if (spaceLeft >= popupWidth + gap) {
    top = rect.top
    left = rect.left - popupWidth - gap
  } else if (spaceBottom >= popupHeight + gap) {
    top = rect.bottom + gap
    left = rect.left
  } else if (spaceRight >= popupWidth + gap) {
    top = rect.top
    left = rect.right + gap
  } else {
    top = rect.top - popupHeight - gap
    left = rect.left
  }

  // 边界钳制
  if (left < 8) left = 8
  if (left + popupWidth > window.innerWidth - 8) left = window.innerWidth - popupWidth - 8
  if (top < 8) top = 8
  if (top + popupHeight > window.innerHeight - 8) top = window.innerHeight - popupHeight - 8

  position.value = { top, left }
}

function selectItem(name: string) {
  emit('select', name)
}

function handleKeydown(e: KeyboardEvent) {
  if (!props.visible) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, props.items.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const item = props.items[activeIndex.value]
    if (item) {
      selectItem(item)
    }
  } else if (e.key === 'Escape') {
    e.preventDefault()
    emit('close')
  }
}

// 点击外部关闭
function handleClickOutside(e: MouseEvent) {
  if (popupRef.value && !popupRef.value.contains(e.target as Node)) {
    emit('close')
  }
}

// 滚动或缩放时关闭弹窗
function handleScrollOrResize() {
  if (props.visible) {
    emit('close')
  }
}

watch(() => props.visible, async (val) => {
  if (val) {
    activeIndex.value = 0
    await nextTick()
    computePosition()
  }
})

watch(() => props.items, () => {
  if (props.visible) computePosition()
})

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('mousedown', handleClickOutside, true)
  window.addEventListener('scroll', handleScrollOrResize, true)
  window.addEventListener('resize', handleScrollOrResize)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('mousedown', handleClickOutside, true)
  window.removeEventListener('scroll', handleScrollOrResize, true)
  window.removeEventListener('resize', handleScrollOrResize)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      ref="popupRef"
      class="quick-input-popup"
      :style="{ top: `${position.top}px`, left: `${position.left}px` }"
    >
      <div class="popup-header">输入绑定</div>
      <div class="popup-list">
        <button
          v-for="(item, index) in items"
          :key="item"
          :class="['popup-item', { active: index === activeIndex }]"
          type="button"
          @click="selectItem(item)"
        >
          <span class="item-name">{{ item }}</span>
          <span class="item-template">$&#123;{{ item }}&#125;</span>
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
.quick-input-popup {
  position: fixed;
  z-index: 9999;
  width: max-content;
  min-width: 200px;
  max-width: 360px;
  max-height: 300px;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  border: 1px solid #e8e8e8;
  overflow: hidden;
}

.popup-header {
  padding: 8px 12px;
  font-size: 12px;
  color: #8c8c8c;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.popup-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  flex: 1;
  padding: 4px;
}

.popup-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 6px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.5;
  color: rgba(0, 0, 0, 0.88);
  transition: background 0.15s;

  &:hover,
  &.active {
    background: #F2F4F7;
  }
}

.item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  font-weight: 500;
}

.item-template {
  flex-shrink: 0;
  margin-left: 16px;
  font-size: 12px;
  color: #8c8c8c;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
</style>

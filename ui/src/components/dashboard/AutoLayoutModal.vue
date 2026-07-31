<script setup lang="ts">
/**
 * 自动排版选择模态框：两种模式（紧凑 / 平铺），带仿真示意图。
 *
 * @author huxuehao
 */
import { ref, watch } from 'vue'
import type { AutoLayoutMode } from './autoLayout'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'apply', mode: AutoLayoutMode): void
}>()

const selected = ref<AutoLayoutMode>('compact')

watch(
  () => props.open,
  (v) => {
    if (v) selected.value = 'compact'
  },
)

function confirm() {
  emit('apply', selected.value)
  emit('update:open', false)
}
</script>

<template>
  <a-modal
    :open="open"
    title="自动排版"
    width="520px"
    ok-text="应用"
    @update:open="emit('update:open', $event)"
    @ok="confirm"
  >
    <p class="al-hint">选择一种排版方式，将重新排列画布中的所有面板（可撤销）。</p>
    <div class="al-options">
      <button
        class="al-card"
        :class="{ active: selected === 'compact' }"
        @click="selected = 'compact'"
      >
        <svg viewBox="0 0 140 84" class="al-art">
          <rect x="0" y="0" width="140" height="84" rx="6" fill="#f7f8fa" />
          <rect x="10" y="8" width="38" height="30" rx="3" fill="#c7d5ee" />
          <rect x="10" y="42" width="38" height="20" rx="3" fill="#c7d5ee" />
          <rect x="54" y="8" width="32" height="22" rx="3" fill="#c7d5ee" />
          <rect x="54" y="34" width="32" height="28" rx="3" fill="#c7d5ee" />
          <rect x="92" y="8" width="38" height="40" rx="3" fill="#c7d5ee" />
          <rect x="92" y="52" width="38" height="10" rx="3" fill="#c7d5ee" />
        </svg>
        <div class="al-title">紧凑</div>
        <div class="al-desc">自动填满空隙、向左上紧贴</div>
      </button>
      <button class="al-card" :class="{ active: selected === 'tidy' }" @click="selected = 'tidy'">
        <svg viewBox="0 0 140 84" class="al-art">
          <rect x="0" y="0" width="140" height="84" rx="6" fill="#f7f8fa" />
          <rect x="10" y="10" width="36" height="30" rx="3" fill="#bcd0f2" />
          <rect x="52" y="10" width="36" height="30" rx="3" fill="#bcd0f2" />
          <rect x="94" y="10" width="36" height="30" rx="3" fill="#bcd0f2" />
          <rect x="10" y="46" width="36" height="28" rx="3" fill="#bcd0f2" />
          <rect x="52" y="46" width="36" height="28" rx="3" fill="#bcd0f2" />
          <rect x="94" y="46" width="36" height="28" rx="3" fill="#bcd0f2" />
        </svg>
        <div class="al-title">平铺</div>
        <div class="al-desc">按顺序从左到右整齐排布</div>
      </button>
    </div>
  </a-modal>
</template>

<style scoped lang="scss">
.al-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: #999;
}

.al-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.al-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  text-align: center;
  transition: border-color 0.2s ease;
}

.al-card:hover {
  border-color: #c8d3e6;
}

.al-card.active {
  border-color: #1677ff;
}

.al-art {
  width: 100%;
  height: auto;
  border-radius: 6px;
}

.al-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.al-desc {
  font-size: 12px;
  color: #999;
}
</style>

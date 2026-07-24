<template>
  <div class="apboa-loading" :class="sizeClass" role="status" aria-live="polite">
    <div class="fly-spinner" aria-hidden="true"></div>
    <div v-if="tip" class="loading-tip">{{ tip }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  /** 加载提示文本 */
  tip?: string
  /** 加载动画尺寸 */
  size?: 'small' | 'default' | 'large'
  /** 是否全屏显示 */
  fullscreen?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'default',
  fullscreen: false
})

const sizeClass = computed(() => ({
  [`size-${props.size}`]: true,
  'fullscreen': props.fullscreen
}))
</script>

<style scoped lang="scss">
.apboa-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 24px;
}

.apboa-loading.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background-color: rgba(255, 255, 255, 0.9);
}

.fly-spinner {
  width: 64px;
  height: 64px;
  border: 5px solid rgba(40, 120, 255, 0.16);
  border-top-color: #2878ff;
  border-right-color: #7b42e8;
  border-radius: 50%;
  animation: fly-spin 0.75s linear infinite;
}

.apboa-loading.size-small .fly-spinner {
  width: 32px;
  height: 32px;
  border-width: 3px;
}

.apboa-loading.size-large .fly-spinner {
  width: 96px;
  height: 96px;
  border-width: 7px;
}

.loading-tip {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
  text-align: center;
}

.apboa-loading.size-small .loading-tip {
  font-size: 12px;
}

.apboa-loading.size-large .loading-tip {
  font-size: 16px;
}

@keyframes fly-spin {
  to { transform: rotate(360deg); }
}
</style>

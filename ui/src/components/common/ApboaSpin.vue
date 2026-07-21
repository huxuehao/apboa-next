<template>
  <div class="apboa-spin-wrapper" :class="{ 'spinning': spinning }">
    <div class="apboa-spin-container" :class="{ 'blur': spinning && showBlur }">
      <slot></slot>
    </div>
    <Transition name="fade">
      <div v-if="spinning" class="apboa-spin-mask">
         <LoadingOutlined style="margin-right: 6px" />加载中
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { LoadingOutlined } from '@ant-design/icons-vue'
interface Props {
  /** 是否显示加载状态 */
  spinning?: boolean
  /** 加载提示文本 */
  tip?: string
  /** 加载动画尺寸 */
  size?: 'small' | 'default' | 'large'
  /** 是否显示背景模糊效果 */
  showBlur?: boolean
}

withDefaults(defineProps<Props>(), {
  spinning: false,
  size: 'default',
  showBlur: true
})
</script>

<style scoped lang="scss">
.apboa-spin-wrapper {
  position: relative;
  display: inline-block;
  width: 100%;
}

.apboa-spin-container {
  height: 100%;
  transition: filter 0.3s ease;
}

.apboa-spin-container.blur {
  filter: blur(1px);
  pointer-events: none;
}

.apboa-spin-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #575757;
  z-index: 10;
  background-color: rgba(255, 255, 255, 0.7);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

/**
 * IconFont 图标组件
 * 基于 iconfont 字体图标封装的通用图标组件，通过图标名称即可使用
 *
 * @example
 * ```vue
 * <IconFont name="nodestart" />
 * <IconFont name="nodeend" :size="20" color="#f00" />
 * <IconFont name="nodellm" :size="24" color="var(--primary)" spin />
 * ```
 *
 * @author huxuehao
 **/
<script setup lang="ts">
import { computed } from 'vue'
import type { IconName } from './icons'
import { ICON_MAP } from './icons'

/**
 * 组件属性定义
 */
const props = withDefaults(
  defineProps<{
    /** 图标名称（必填），对应 iconfont.css 中 icon- 前缀后的名称 */
    name: IconName
    /** 图标大小，支持数字（px）或 CSS 尺寸字符串，默认继承父级字号 */
    size?: string | number
    /** 图标颜色，支持任意 CSS 颜色值，默认继承当前文本颜色 */
    color?: string
    /** 是否启用旋转动画 */
    spin?: boolean
  }>(),
  {
    size: '1em',
    color: 'currentColor',
    spin: false,
  },
)

/**
 * 格式化 size 值，数字自动补 px 单位
 */
function formatSize(size: string | number): string {
  if (typeof size === 'number') {
    return `${size}px`
  }
  return size
}

/**
 * 图标对应的 CSS class 名
 */
const iconClass = computed(() => `iconfont icon-${props.name}`)

/**
 * 内联样式，控制大小与颜色
 */
const iconStyle = computed(() => ({
  fontSize: formatSize(props.size),
  color: props.color,
}))
</script>

<template>
  <i
    :class="[iconClass, { 'iconfont-spin': spin }]"
    :style="iconStyle"
    :title="ICON_MAP[name]?.label"
    role="img"
    :aria-label="ICON_MAP[name]?.label"
  />
</template>

<style scoped>
/**
 * 旋转动画
 */
.iconfont-spin {
  display: inline-block;
  animation: iconfont-rotate 1s linear infinite;
}

@keyframes iconfont-rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>

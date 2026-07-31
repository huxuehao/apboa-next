<script setup lang="ts">
/**
 * 快捷方式面板：点击跳转到目标。打开方式分本页/新页，站内路由或外链由代码自动判断。
 * 名称必填；头像(图标)与描述缺省时不显示并自适应布局。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'
import { resolveIcon } from '../../icons/iconRegistry'

const props = defineProps<{
  panel: PanelDsl
  data?: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
  interactive?: boolean
  /** 设计器中该面板是否被选中（用于编辑态预览） */
  selected?: boolean
}>()

const router = useRouter()

const name = computed(() => (props.panel.options?.name as string) || '快捷方式')
const desc = computed(() => (props.panel.options?.desc as string) || '')
const iconComp = computed(() => resolveIcon(props.panel.options?.icon as string | undefined))
const hasUrl = computed(() => !!(props.panel.options?.url as string))

// 外观：风格变体 + 头像颜色/背景/圆角/边框/尺寸
const variant = computed(() => (props.panel.options?.variant as string) || 'left')
const avatarSize = computed(() => (props.panel.options?.avatarSize as string) || 'medium')
const SIZE_MAP: Record<string, { box: number; font: number }> = {
  small: { box: 32, font: 16 },
  medium: { box: 40, font: 20 },
  large: { box: 56, font: 28 },
}

const avatarStyle = computed(() => {
  const opts = props.panel.options || {}
  const size = SIZE_MAP[avatarSize.value] || SIZE_MAP.medium!
  const color = (opts.avatarColor as string) || '#1677ff'
  const minimal = variant.value === 'minimal'
  return {
    width: size.box + 'px',
    height: size.box + 'px',
    fontSize: size.font + 'px',
    color,
    // 极简风格：图标无背景/边框，与文字并排
    background: minimal ? 'transparent' : (opts.avatarBg as string) || '#f0f5ff',
    borderRadius: (Number(opts.avatarRadius) || 0) + 'px',
    border: !minimal && opts.avatarBorder === true ? `1px solid ${color}` : 'none',
  }
})

// 编辑态下已选中即可预览跳转；运行态始终可点
const clickable = computed(
  () => props.interactive !== false || (props.selected && hasUrl.value),
)

/** 是否外部链接：http(s):// 或 // 开头视为外链，否则按站内路由处理 */
function isExternal(url: string): boolean {
  return /^https?:\/\//i.test(url) || url.startsWith('//')
}

/** 跳转到目标；forceNewTab=true 用于编辑态预览（统一新标签，不破坏编辑会话）。
 *  打开方式仅本页/新页两种，站内路由或外链由 isExternal 自动判断 */
function open(forceNewTab: boolean) {
  const url = (props.panel.options?.url as string) || ''
  if (!url) return
  const newPage = forceNewTab || (props.panel.options?.target as string) === 'blank'
  const external = isExternal(url)
  if (newPage) {
    window.open(external ? url : router.resolve(url).href, '_blank')
    return
  }
  if (external) {
    window.location.href = url
  } else {
    router.push(url)
  }
}

function onClick() {
  if (props.interactive === false) {
    // 设计器：首次点击交给画布选中，已选中再点则在新标签预览
    if (props.selected) open(true)
    return
  }
  
  open(false)
}
</script>

<template>
  <div class="shortcut-panel" :class="[variant, { clickable }]" @click="onClick">
    <span v-if="iconComp" class="shortcut-avatar" :style="avatarStyle">
      <component :is="iconComp" />
    </span>
    <div class="shortcut-body">
      <div class="shortcut-name">{{ name }}</div>
      <div v-if="desc" class="shortcut-desc">{{ desc }}</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.shortcut-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 100%;
  padding: 6px 8px;
  border-radius: 8px;
  transition: background 0.2s ease;
}

.shortcut-panel.clickable {
  cursor: pointer;
}

.shortcut-panel.clickable:hover {
  background: #f7f8fa;
}

/* 居中风格：上图标下文字 */
.shortcut-panel.center {
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  text-align: center;
}

.shortcut-panel.center .shortcut-body {
  flex: 0 0 auto;
  width: 100%;
}

/* 极简风格：紧凑行内 */
.shortcut-panel.minimal {
  gap: 8px;
}

.shortcut-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.shortcut-body {
  flex: 1;
  min-width: 0;
}

.shortcut-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shortcut-desc {
  margin-top: 4px;
  font-size: 13px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

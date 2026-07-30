<script setup lang="ts">
/**
 * 快捷方式面板：点击跳转到目标路由/链接。支持站内跳转、新标签页、外部链接。
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
}>()

const router = useRouter()

const name = computed(() => (props.panel.options?.name as string) || '快捷方式')
const desc = computed(() => (props.panel.options?.desc as string) || '')
const iconComp = computed(() => resolveIcon(props.panel.options?.icon as string | undefined))

function onClick() {
  // 设计器编辑态不触发跳转（interactive=false 时仅用于选中）
  if (props.interactive === false) return
  const url = (props.panel.options?.url as string) || ''
  if (!url) return
  const target = (props.panel.options?.target as string) || 'route'
  if (target === 'blank') {
    window.open(url, '_blank')
  } else if (target === 'external') {
    window.location.href = url
  } else {
    router.push(url)
  }
}
</script>

<template>
  <div class="shortcut-panel" :class="{ clickable: interactive !== false }" @click="onClick">
    <span v-if="iconComp" class="shortcut-avatar">
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
  padding: 4px 2px;
}

.shortcut-panel.clickable {
  cursor: pointer;
}

.shortcut-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 8px;
  background: #f0f5ff;
  color: #1677ff;
  font-size: 20px;
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

<script setup lang="ts">
import { computed, h } from 'vue'
import {
  BranchesOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  EllipsisOutlined,
  FormOutlined,
  LockFilled,
  WarningFilled,
} from '@ant-design/icons-vue'
import type { Workflow } from '@/types/workflow'

const props = defineProps<{
  data: Workflow
}>()

const emit = defineEmits<{
  edit: [workflow: Workflow]
  design: [workflow: Workflow]
  copy: [workflow: Workflow]
  remove: [workflow: Workflow]
}>()

const statusText = computed(() => (props.data.status === 'PUBLISHED' ? '已发布' : '草稿'))
const formattedTime = computed(() => {
  if (!props.data.updatedAt) return '暂无更新'
  return new Date(props.data.updatedAt).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const nodeCount = computed(() => props.data.config?.nodes?.length || 0)
const edgeCount = computed(() => props.data.config?.edges?.length || 0)
const isDisabled = computed(() => props.data.enabled === false)
const isLocked = computed(() => Number(props.data.locked || 0) === 1)
const isPublished = computed(() => props.data.status === 'PUBLISHED')

const avatarClass = computed(() => ({
  published: isPublished.value,
  disabled: isDisabled.value,
  locked: isLocked.value,
}))

const avatarTooltip = computed(() => {
  if (isDisabled.value) return '已禁用'
  if (isLocked.value) return '已锁定'
  if (isPublished.value) return '已发布'
  return '未发布'
})

const cornerBadgeType = computed(() => {
  if (isDisabled.value) return 'disabled'
  if (isLocked.value) return 'locked'
  if (isPublished.value) return 'published'
  return 'draft'
})

const menuItems = computed(() => [
  { key: 'edit', label: '编辑', disabled: isLocked.value, icon: () => h(EditOutlined) },
  { key: 'copy', label: '复制', icon: () => h(CopyOutlined) },
  { key: 'design', label: '设计', icon: () => h(FormOutlined) },
  { type: 'divider' },
  { key: 'remove', label: '删除', danger: true, icon: () => h(DeleteOutlined) },
])

function handleMenu({ key }: { key: string }) {
  const actions: Record<string, () => void> = {
    edit: () => emit('edit', props.data),
    copy: () => emit('copy', props.data),
    design: () => emit('design', props.data),
    remove: () => emit('remove', props.data),
  }
  actions[key]?.()
}
</script>

<template>
  <article class="workflow-card">
    <header class="card-header">
      <ATooltip :title="avatarTooltip" placement="top">
        <div class="card-avatar-wrapper">
          <div class="card-avatar" :class="avatarClass">
            <BranchesOutlined class="avatar-icon" />
          </div>
          <span class="avatar-corner-badge" :class="`badge-${cornerBadgeType}`">
            <CloseCircleFilled v-if="cornerBadgeType === 'disabled'" />
            <LockFilled v-else-if="cornerBadgeType === 'locked'" />
            <CheckCircleFilled v-else-if="cornerBadgeType === 'published'" />
            <WarningFilled v-else />
          </span>
        </div>
      </ATooltip>

      <button type="button" class="card-name" :title="data.name" @click="emit('design', data)">
        {{ data.name || '未命名工作流' }}
      </button>

      <ADropdown :trigger="['hover']">
        <AButton type="text" size="small">
          <template #icon><EllipsisOutlined /></template>
        </AButton>
        <template #overlay>
          <AMenu :items="menuItems" @click="handleMenu" />
        </template>
      </ADropdown>
    </header>

    <div class="card-content">
      <div class="desc" :title="data.remark">{{ data.remark || '暂无描述，进入编辑完善工作流说明。' }}</div>
      <div class="metrics">

        <span>节点 {{ nodeCount }}</span>
        <span> · 连线 {{ edgeCount }}</span>
      </div>
    </div>

    <footer class="card-footer">
      <div class="tags">
        <ATag :bordered="false">{{ statusText }}</ATag>
        <ATag color="default" :bordered="false">v{{ data.version || '0' }}</ATag>
      </div>
      <div class="time">更新于 {{ formattedTime }}</div>
    </footer>
  </article>
</template>

<style scoped lang="scss">
.workflow-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  min-height: 180px;
  padding: var(--spacing-md);
  border-radius: var(--border-radius-lg);
  background: var(--color-bg-white);
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
  transition: all var(--transition-base);
}

.workflow-card:hover {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 32px;
  gap: var(--spacing-sm);
  align-items: center;
}

.card-avatar-wrapper {
  position: relative;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.card-avatar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  overflow: hidden;
  border-radius: var(--border-radius-xl);
  background: #fff7e6;
  color: #fa8c16;
}

.card-avatar.published {
  background: #e6f4ff;
  color: #1677ff;
}

.card-avatar.disabled {
  background: #f5f5f5;
  color: #8c8c8c;
}

.card-avatar.locked {
  background: #ffebee;
  color: #f44336;
}

.avatar-icon {
  font-size: 20px;
}

.avatar-corner-badge {
  position: absolute;
  right: -4px;
  bottom: -4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 0 2px var(--color-bg-white);
  font-size: 12px;
}

.badge-published {
  color: #1677ff;
}

.badge-draft {
  color: #faad14;
}

.badge-locked {
  color: #ff4d4f;
}

.badge-disabled {
  color: #8c8c8c;
}

.card-name {
  min-width: 0;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  font-size: var(--font-size-base);
  font-weight: 700;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-content {
  display: grid;
  gap: 10px;
  min-height: 68px;
}

.desc {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: auto;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.time {
  flex-shrink: 0;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}
</style>

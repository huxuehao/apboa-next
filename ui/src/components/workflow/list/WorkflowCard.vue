<script setup lang="ts">
import { computed, h } from 'vue'
import {
  BranchesOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  EllipsisOutlined,
  LockOutlined,
  PlayCircleOutlined,
  RocketOutlined,
  UnlockOutlined,
} from '@ant-design/icons-vue'
import type { Workflow } from '@/types/workflow'

const props = defineProps<{
  data: Workflow
}>()

const emit = defineEmits<{
  edit: [workflow: Workflow]
  copy: [workflow: Workflow]
  publish: [workflow: Workflow]
  debug: [workflow: Workflow]
  lock: [workflow: Workflow]
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

const menuItems = computed(() => [
  { key: 'edit', label: '设计', icon: () => h(EditOutlined) },
  { key: 'copy', label: '复制', icon: () => h(CopyOutlined) },
  { key: 'publish', label: '发布', icon: () => h(RocketOutlined) },
  { key: 'debug', label: '调试运行', icon: () => h(PlayCircleOutlined) },
  { key: 'lock', label: props.data.locked ? '解锁编辑' : '锁定编辑', icon: () => h(props.data.locked ? UnlockOutlined : LockOutlined) },
  { type: 'divider' },
  { key: 'remove', label: '删除', danger: true, icon: () => h(DeleteOutlined) },
])

function handleMenu({ key }: { key: string }) {
  const actions: Record<string, () => void> = {
    edit: () => emit('edit', props.data),
    copy: () => emit('copy', props.data),
    publish: () => emit('publish', props.data),
    debug: () => emit('debug', props.data),
    lock: () => emit('lock', props.data),
    remove: () => emit('remove', props.data),
  }
  actions[key]?.()
}
</script>

<template>
  <article class="workflow-card">
    <header class="card-header">
      <div class="card-avatar" :class="{ published: data.status === 'PUBLISHED' }">
        <BranchesOutlined />
      </div>
      <button type="button" class="card-name" :title="data.name" @click="emit('edit', data)">
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
      <div class="desc" :title="data.remark">{{ data.remark || '暂无描述，进入设计器完善工作流说明。' }}</div>
      <div class="metrics">
        <span>节点 {{ nodeCount }}</span>
        <span>连线 {{ edgeCount }}</span>
        <span>{{ data.routeId || '未绑定路由' }}</span>
      </div>
    </div>

    <footer class="card-footer">
      <div class="tags">
        <ATag :color="data.status === 'PUBLISHED' ? 'green' : 'blue'" :bordered="false">{{ statusText }}</ATag>
        <ATag v-if="data.locked" color="default" :bordered="false">已锁定</ATag>
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

.card-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--border-radius-xl);
  background: #e6f4ff;
  color: #1677ff;
  font-size: 20px;
}

.card-avatar.published {
  background: #f6ffed;
  color: #52c41a;
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

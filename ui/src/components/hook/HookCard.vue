/**
 * 扩展配置卡片组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { EllipsisOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import hookAvatar from '@/assets/avatar/hook.png'
import type { HookConfigVO } from '@/types'
import * as hookApi from '@/api/hook'
import {
  createViewItem,
  createEditItem,
  createEnableItem,
  createDeleteItem,
  createRenameItem,
  createDivider,
} from '@/composables/useCardMenuItems'

/**
 * Props定义
 */
const props = defineProps<{
  data: HookConfigVO
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  view: [id: string]
  edit: [id: string]
  enable: [id: string]
  delete: [id: string]
  renamed: [id: string]
}>()

/**
 * 操作菜单项
 */
const menuItems = computed(() => {
  const items = [
    createViewItem(),
    createEnableItem(props.data.enabled as boolean),
  ]
  if (props.data.hookType === 'CUSTOM') {
    items.push(createEditItem())
    items.push(createDivider())
    items.push(createDeleteItem())
  } else {
    // 内置钩子只读，但允许改展示名称（不影响生效，启动同步不覆盖）
    items.push(createRenameItem())
  }
  return items
})

/**
 * 格式化更新时间
 */
const formattedTime = computed(() => {
  if (!props.data.updatedAt) return ''
  const date = new Date(props.data.updatedAt)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
})

/**
 * 扩展类型显示文本
 */
const hookTypeText = computed(() => {
  return props.data.hookType === 'BUILTIN' ? '内置' : '自定义'
})

// 改名弹窗
const renameModalVisible = ref(false)
const renameValue = ref('')

/**
 * 打开改名弹窗
 */
function openRenameModal() {
  renameValue.value = props.data.name || ''
  renameModalVisible.value = true
}

/**
 * 确认改名
 */
async function handleRenameConfirm() {
  const name = renameValue.value.trim()
  if (!name) {
    message.warning('请输入名称')
    return
  }
  try {
    await hookApi.updateName(String(props.data.id), name)
    message.success('改名成功')
    renameModalVisible.value = false
    emit('renamed', props.data.id as string)
  } catch {
    message.error('改名失败')
  }
}

/**
 * 处理菜单点击
 */
function handleMenuClick({ key }: { key: string }) {
  switch (key) {
    case 'view':
      emit('view', props.data.id as string)
      break
    case 'edit':
      emit('edit', props.data.id as string)
      break
    case 'enable':
      emit('enable', props.data.id as string)
      break
    case 'delete':
      emit('delete', props.data.id as string)
      break
    case 'rename':
      openRenameModal()
      break
  }
}
</script>

<template>
  <div class="hook-card">
    <div class="card-header flex items-center gap-sm">
      <div class="card-avatar flex-center" :class="{ disabled: !data.enabled }"><img :src="hookAvatar" alt="hook" /></div>
      <div class="card-name flex-1 truncate" :title="data.name" @click="emit('view', data.id as string)">{{ data.name }}</div>
      <ADropdown :trigger="['hover']">
        <AButton type="text" size="small" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
          <EllipsisOutlined />
        </AButton>
        <template #overlay>
          <AMenu @click="handleMenuClick" :items="menuItems"></AMenu>
        </template>
      </ADropdown>
    </div>

    <div class="card-content line-clamp-3" :title="data.description">
      {{ data.description || '暂无描述' }}
    </div>

    <div class="card-footer flex items-center justify-between">
      <div class="card-tags flex items-center gap-xs">
        <ATag color="default" class="tag">{{ hookTypeText }}</ATag>
      </div>
      <div class="card-time text-placeholder text-xs">更新于 {{ formattedTime }}</div>
    </div>
  </div>

  <!-- 改名弹窗 -->
  <a-modal
    v-model:open="renameModalVisible"
    title="修改名称"
    :ok-text="'确定'"
    :cancel-text="'取消'"
    @ok="handleRenameConfirm"
    destroyOnClose
  >
    <a-form layout="vertical">
      <a-form-item label="展示名称（仅界面展示，不影响钩子实际生效）">
        <a-input v-model:value="renameValue" placeholder="给这个钩子起个易懂的名字" allow-clear />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped lang="scss">
.hook-card {
  min-height: 180px;
  padding: var(--spacing-md);
  background-color: #FFFFFF;
  border-radius: var(--border-radius-lg);
  border: 1px solid #ebebeb;
  transition: all var(--transition-base);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);

  &:hover {
    box-shadow: 0 4px 6px -5px rgba(0, 0, 0, 0.3);
    transform: translateY(-2px);
  }

  .card-header {
    .card-avatar {
      width: 40px;
      height: 40px;
      background-color: #e8eaf6;
      border-radius: var(--border-radius-xl);
      flex-shrink: 0;

      img {
        width: 28px;
        height: 28px;
        object-fit: contain;
      }
    }

    .card-name {
      font-size: var(--font-size-base);
      font-weight: 600;
      color: var(--color-text-primary);
      cursor: pointer;
      transition: color var(--transition-base);
    }
  }

  .card-content {
    font-size: var(--font-size-sm);
    color: var(--color-text-regular);
    line-height: 1.6;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    overflow: hidden;
    text-overflow: ellipsis;
    word-break: break-all;
    min-height: 65px;
    max-height: 65px;
  }

  .card-footer {
    padding-top: var(--spacing-xs);

    .card-tags {
      flex-wrap: wrap;
    }

    .card-time {
      white-space: nowrap;
    }
  }

  .disabled {
    color: #757575 !important;
    background-color: #e7e7e7 !important;
    img {
      filter: grayscale(100%);
      opacity: 0.5;
    }
  }
}
</style>

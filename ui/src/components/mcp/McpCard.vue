/**
 * MCP 服务配置卡片组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleFilled,
  CloseCircleFilled,
  EllipsisOutlined,
  LoadingOutlined,
  WarningFilled
} from '@ant-design/icons-vue'
import mcpAvatar from '@/assets/avatar/mcp.png'
import type { McpServerVO } from '@/types'
import { McpActivationStatus } from '@/types'
import {
  createActivateItem,
  createDeleteItem,
  createDivider,
  createEditItem,
  createEnableItem,
  createSyncItem,
  createToolGovernanceItem,
  createViewItem
} from '@/composables/useCardMenuItems'
import { getMcpPrimaryAction } from '@/composables/useMcpPresentation'

const props = defineProps<{
  data: McpServerVO
}>()

const emit = defineEmits<{
  view: [id: string]
  edit: [id: string]
  delete: [id: string]
  enable: [id: string]
  activate: [id: string]
  sync: [id: string]
  toolGovernance: [id: string]
}>()

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

const modeText = computed(() => {
  return props.data.mode === 'SYNC' ? '同步' : '异步'
})

const primaryAction = computed(() => getMcpPrimaryAction(props.data))

/* ============================================================
 * 头像角标状态系统
 * 六种状态：停用 disabled | 待连接 not-activated | 连接中 activating
 *          | 已激活 active | 激活失败 failed | 激活中无工具 active-no-tool
 * ============================================================ */

const isDisabled = computed(() => !props.data.enabled)

const isActivating = computed(() => props.data.activationStatus === McpActivationStatus.ACTIVATING)

const isFailed = computed(() => props.data.activationStatus === McpActivationStatus.FAILED)

const isActive = computed(() => props.data.activationStatus === McpActivationStatus.ACTIVE)

const isNotActivated = computed(() => props.data.activationStatus === McpActivationStatus.NOT_ACTIVATED)

/** 已激活但无工具（有连接但工具目录为空） */
const isActiveNoTool = computed(() => isActive.value && (props.data.toolCount || 0) === 0)

/** 头像样式类：仅 success 为正常色，其余灰色 */
const avatarClass = computed(() => ({
  success: isActive.value && !isActiveNoTool.value && !isDisabled.value
}))

/**
 * 角标提示文本
 * 仅当角标可见时提供 tooltip 内容
 */
const cornerTooltip = computed(() => {
  if (isDisabled.value) return 'MCP 服务已停用'
  if (isActivating.value) return 'MCP 服务正在连接中，请稍候'
  if (isNotActivated.value) {
    return 'MCP 服务尚未连接，请通过菜单中的「连接」操作发起连接并刷新工具目录'
  }
  if (isFailed.value) {
    return props.data.activationMessage || 'MCP 服务连接失败，请通过菜单中的「重试连接」操作重新连接'
  }
  if (isActive.value) {
    const total = props.data.toolCount || 0
    const available = props.data.availableToolCount || 0
    return `工具总数: ${total}，全局可用: ${available}`
  }
  return '未知状态'
})

/**
 * 角标内容类型
 * - none:     不显示角标（停用 / 激活中）
 * - warning:  黄色叹号（待连接 / 已连接无工具）
 * - error:    红色叉号（激活失败）
 * - success:  绿色对号（已激活且有工具）
 */
type CornerBadgeType = 'none' | 'warning' | 'error' | 'success'

const cornerBadgeType = computed<CornerBadgeType>(() => {
  if (isDisabled.value || isActivating.value) return 'none'
  if (isFailed.value) return 'error'
  if (isNotActivated.value || isActiveNoTool.value) return 'warning'
  if (isActive.value) return 'success'
  return 'none'
})

const menuItems = computed(() => {
  const items = [
    createViewItem(),
    createEditItem()
  ]

  if (primaryAction.value?.key === 'activate') {
    items.push(createActivateItem(primaryAction.value.label))
  } else if (primaryAction.value?.key === 'sync') {
    items.push(createSyncItem(primaryAction.value.label))
  }

  items.push(
    createToolGovernanceItem(),
    createEnableItem(props.data.enabled),
    createDivider(),
    createDeleteItem()
  )

  return items
})

function handleMenuClick({ key }: { key: string }) {
  switch (key) {
    case 'view':
      emit('view', props.data.id as string)
      break
    case 'edit':
      emit('edit', props.data.id as string)
      break
    case 'activate':
      emit('activate', props.data.id as string)
      break
    case 'sync':
      emit('sync', props.data.id as string)
      break
    case 'toolGovernance':
      emit('toolGovernance', props.data.id as string)
      break
    case 'enable':
      emit('enable', props.data.id as string)
      break
    case 'delete':
      emit('delete', props.data.id as string)
      break
  }
}
</script>

<template>
  <div class="mcp-card">
    <div class="card-header flex items-center gap-sm">
      <div class="card-avatar-wrapper">
        <ATooltip :title="cornerTooltip" placement="top">
          <div class="card-avatar flex-center" :class="avatarClass">
            <LoadingOutlined v-if="isActivating" spin  style="font-size: 18px;" />
            <img v-else :src="mcpAvatar" alt="mcp" />
          </div>
          <!-- 角标 -->
          <span class="avatar-corner-badge" :class="`badge-${cornerBadgeType}`">
            <WarningFilled v-if="cornerBadgeType === 'warning'" />
            <CloseCircleFilled v-else-if="cornerBadgeType === 'error'" />
            <CheckCircleFilled v-else-if="cornerBadgeType === 'success'" />
          </span>
        </ATooltip>
      </div>
      <div class="card-name flex-1 truncate" :title="data.name" @click="emit('view', data.id as string)">
        {{ data.name }}
      </div>
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
      {{ data.description }}
    </div>

    <div class="card-footer flex items-center justify-between">
      <div class="card-tags flex items-center gap-xs">
        <ATag color="default" class="tag">{{ data.protocol }}</ATag>
        <ATag color="default" class="tag">{{ modeText }}</ATag>
      </div>
      <div class="card-time text-placeholder text-xs">更新于 {{ formattedTime }}</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mcp-card {
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
    .card-avatar-wrapper {
      position: relative;
      flex-shrink: 0;
      cursor: pointer;
    }

    .card-avatar {
      width: 40px;
      height: 40px;
      background-color: #e7e7e7;
      border-radius: var(--border-radius-xl);
      flex-shrink: 0;
      transition: background-color var(--transition-base), color var(--transition-base);

      img {
        width: 30px;
        height: 30px;
        object-fit: contain;
        filter: grayscale(100%);
        opacity: 0.5;
      }

      /* 正常态（已激活且有工具） */
      &.success {
        background-color: #e8eaf6;

        img {
          filter: none;
          opacity: 1;
        }
      }
    }

    /* 角标 - 定位于头像右下角 */
    .avatar-corner-badge {
      position: absolute;
      bottom: -4px;
      right: -4px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      box-shadow: 0 0 0 2px var(--color-bg-white);
      cursor: pointer;
      font-size: 12px;

      &.badge-warning,
      &.badge-error {
        width: 18px;
        height: 18px;
        background: #fff;
        color: #8c8c8c;
      }

      &.badge-success {
        width: 18px;
        height: 18px;
        background: #fff;
        color: #1677ff;
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
}
</style>

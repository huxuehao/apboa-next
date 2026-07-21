/**
 * 自动化任务列表项组件
 * 展示单个自动化任务的信息和操作
 *
 * @component
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  HistoryOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import { Tooltip as ATooltip } from 'ant-design-vue'
import agentAvatar from '@/assets/avatar/agent.png'
import workflowAvatar from '@/assets/avatar/workflow.png'
import type { JobInfo, TenantRole } from '@/types'
import { useAccountStore } from '@/stores'
import * as automationApi from '@/api/automation'
import SimpleSwitch from '@/components/common/SimpleSwitch.vue'

const router = useRouter()
const accountStore = useAccountStore()

const props = defineProps<{
  data: JobInfo
}>()

const emit = defineEmits<{
  edit: [job: JobInfo]
  refresh: []
}>()

const avatarSrc = computed(() => {
  return props.data.type === 'AGENT' ? agentAvatar : workflowAvatar
})

const targetTypeLabel = computed(() => {
  return props.data.type === 'AGENT' ? '智能体' : '工作流'
})

/**
 * 从 dataMap 解析目标名称
 */
const targetName = computed(() => {
  try {
    const dataMap = JSON.parse(props.data.dataMap || '{}')
    return `${dataMap?.jobName || '未命名'}（${dataMap?.bizName || '无'}）`
  } catch {
    return ''
  }
})

/**
 * Cron 可读描述
 */
const cronDescription = computed(() => {
  const cron = props.data.cron
  if (!cron) return ''
  const parts = cron.split(' ')
  if (parts.length !== 6 && parts.length !== 7) return '格式不正确'

  const [second, minute, hour, day, month, week] = parts as [string, string, string, string, string, string]
  if (second === '0' && minute === '0' && hour === '0' && day === '*' && month === '*' && week === '?') return '每天零点执行'
  if (second === '0' && minute === '0' && hour === '*' && day === '*' && month === '*' && week === '?') return '每小时执行'
  if (second === '0' && minute === '*/5' && hour === '*' && day === '*' && month === '*' && week === '?') return '每5分钟执行'

  const desc: string[] = []
  if (week === 'MON-FRI') desc.push('工作日')
  else if (week === 'SUN,SAT') desc.push('周末')
  if (hour !== '*') desc.push(`${hour}点`)
  if (minute && minute !== '0' && minute !== '*') {
    desc.push(minute.startsWith('*/') ? `每${minute.replace('*/', '')}分钟` : `${minute}分`)
  }
  return desc.length > 0 ? desc.join('，') : '自定义执行策略'
})

/**
 * 格式化创建时间
 */
const formattedTime = computed(() => {
  // JobInfo 可能没有 createdAt 字段
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const createdAt = (props.data as Record<string, any>).createdAt
  if (!createdAt) return ''
  const date = new Date(createdAt)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
})

const toggleLoading = ref(false)

/**
 * 切换启用状态
 */
async function handleToggle() {
  toggleLoading.value = true
  try {
    await automationApi.toggleJob(props.data.id!)
    message.success(props.data.enabled ? '已禁用' : '已启用')
    emit('refresh')
  } catch (e) {
    console.error('切换状态失败:', e)
  } finally {
    toggleLoading.value = false
  }
}

/**
 * 手动执行
 */
async function handleTrigger() {
  Modal.confirm({
    title: '确认执行',
    content: '是否继续执行该任务？',
    okText: '确认',
    cancelText: '取消',
    async onOk() {
      try {
        message.loading('正在执行...', 0)
        await automationApi.triggerJob(props.data.id!)
        message.destroy()
        message.success('执行成功')
        emit('refresh')
      } catch (e) {
        message.destroy()
        console.error('手动执行失败:', e)
      }
    }
  })
}

/**
 * 查看执行记录
 */
function handleRecords() {
  router.push(`/automation/${props.data.id}/records`)
}

/**
 * 删除任务
 */
function handleDelete() {

  if(props.data.enabled) {
    message.warning('请先停止任务')
    return
  }

  Modal.confirm({
    title: '确认删除',
    content: '删除后无法恢复，是否继续？',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      try {
        await automationApi.deleteJob(props.data.id!)
        message.success('删除成功')
        emit('refresh')
      } catch (e) {
        console.error('删除失败:', e)
      }
    }
  })
}
</script>

<template>
  <div class="automation-item">
    <!-- 头像 -->
    <div class="item-avatar-wrapper">
      <div class="item-avatar">
        <img :src="avatarSrc" :alt="targetTypeLabel" :class="{ 'avatar-disabled': !data.enabled }" />
      </div>
    </div>

    <!-- 主信息区 -->
    <div class="item-main">
      <div class="item-header">
        <span class="item-name">
          {{ targetName || '未命名任务' }}
        </span>
      </div>
      <div class="item-meta">
        <span class="item-cron">{{ cronDescription }}</span>
        <span>·</span>
        <span class="item-time">创建于 {{ formattedTime }}</span>
      </div>
    </div>

    <!-- 操作区 -->
    <div class="item-actions">
      <SimpleSwitch
        :checked="data.enabled"
        :loading="toggleLoading"
        size="small"
        :disabled="!accountStore.hasAnyRole(['TENANT_EDITOR' as TenantRole, 'TENANT_ADMIN' as TenantRole, 'TENANT_OWNER' as TenantRole])"
        @change="handleToggle"
      />
      <span class="actions-divider" />
      <ATooltip title="编辑" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
        <AButton type="text"  @click="emit('edit', data)">
          <template #icon><EditOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip title="执行一次" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
        <AButton type="text" @click="handleTrigger">
          <template #icon><PlayCircleOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip title="执行记录">
        <AButton type="text" @click="handleRecords">
          <template #icon><HistoryOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip title="删除" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
        <AButton type="text"  danger @click="handleDelete">
          <template #icon><DeleteOutlined /></template>
        </AButton>
      </ATooltip>
    </div>
  </div>
</template>

<style scoped lang="scss">
.automation-item {
  display: flex;
  align-items: center;
  padding: 16px 20px;
   border: 1px solid #EBEBEB;
  border-radius: 8px;
  transition: background-color 0.2s ease;
  cursor: default;
  gap: 16px;
  margin-bottom: 10px;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02);
  }
}

.item-avatar-wrapper {
  flex-shrink: 0;
}

.item-avatar {
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 35px;
    height: 35px;
    object-fit: contain;
  }

  .avatar-disabled {
    filter: grayscale(100%);
    opacity: 0.5;
  }
}

.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.item-cron {
  white-space: nowrap;
}

.item-time {
  white-space: nowrap;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.actions-divider {
  width: 1px;
  height: 16px;
  background-color: #e8e8e8;
  margin: 0 2px;
}
</style>

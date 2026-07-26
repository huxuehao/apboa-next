/**
 * API服务列表项组件
 * 展示单个API的信息（方法、路径、分类、所属应用、绑定工作流）和操作
 *
 * @component
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import { Tooltip as ATooltip } from 'ant-design-vue'
import workflowAvatar from '@/assets/avatar/workflow.png'
import type { TenantRole } from '@/types'
import type { GatewayApi } from '@/types/apiService'
import { useAccountStore } from '@/stores'
import * as apiServiceApi from '@/api/apiService'
import SimpleSwitch from '@/components/common/SimpleSwitch.vue'

const accountStore = useAccountStore()

const props = defineProps<{
  data: GatewayApi
}>()

const emit = defineEmits<{
  edit: [api: GatewayApi]
  refresh: []
}>()

const isOnline = computed(() => props.data.online === 1)

/** 请求方法徽标颜色 */
const methodColor = computed(() => {
  switch (props.data.method) {
    case 'GET': return '#2f9e44'
    case 'POST': return '#1971c2'
    case 'PUT': return '#e8590c'
    case 'DELETE': return '#e03131'
    case 'PATCH': return '#9c36b5'
    default: return '#495057'
  }
})

/** 鉴权标签 */
const authLabel = computed(() => {
  return props.data.config?.authType === 'NONE' ? '免鉴权' : '平台鉴权'
})

/**
 * 格式化创建时间
 */
const formattedTime = computed(() => {
  if (!props.data.createdAt) return ''
  const date = new Date(props.data.createdAt)
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
 * 切换上下线状态
 */
async function handleToggle() {
  toggleLoading.value = true
  try {
    await apiServiceApi.toggleApiOnline(props.data.id!, isOnline.value ? 0 : 1)
    message.success(isOnline.value ? '已下线' : '已上线')
    emit('refresh')
  } catch (e) {
    console.error('切换状态失败:', e)
  } finally {
    toggleLoading.value = false
  }
}

/**
 * 删除API
 */
function handleDelete() {
  if (isOnline.value) {
    message.warning('请先下线API')
    return
  }

  Modal.confirm({
    title: '确认删除',
    content: '删除后将同时移除工作流绑定与客户端授权，且无法恢复，是否继续？',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      try {
        await apiServiceApi.deleteApis([props.data.id!])
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
  <div class="api-service-item">
    <!-- 方法徽标 -->
    <div class="item-method" :style="{ color: methodColor, borderColor: methodColor }">
      {{ data.method }}
    </div>

    <!-- 主信息区 -->
    <div class="item-main">
      <div class="item-header">
        <span class="item-name">{{ data.name }}</span>
        <span class="item-path">{{ data.path }}</span>
        <ATag v-if="data.category" class="item-tag">{{ data.category }}</ATag>
      </div>
      <div class="item-meta">
        <span v-if="data.appName">{{ data.appName }}（:{{ data.appPort }}）</span>
        <span v-if="data.appName">·</span>
        <span class="item-workflow">
          <img :src="workflowAvatar" alt="workflow" />
          {{ data.workflowName || '未绑定工作流' }}
        </span>
        <span>·</span>
        <span>{{ authLabel }}</span>
        <span v-if="formattedTime">·</span>
        <span v-if="formattedTime">创建于 {{ formattedTime }}</span>
      </div>
    </div>

    <!-- 操作区 -->
    <div class="item-actions">
      <SimpleSwitch
        :checked="isOnline"
        :loading="toggleLoading"
        size="small"
        :disabled="!accountStore.hasAnyRole(['TENANT_EDITOR' as TenantRole, 'TENANT_ADMIN' as TenantRole, 'TENANT_OWNER' as TenantRole])"
        @change="handleToggle"
      />
      <span class="actions-divider" />
      <ATooltip title="编辑" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
        <AButton type="text" @click="emit('edit', data)">
          <template #icon><EditOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip title="删除" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
        <AButton type="text" danger @click="handleDelete">
          <template #icon><DeleteOutlined /></template>
        </AButton>
      </ATooltip>
    </div>
  </div>
</template>

<style scoped lang="scss">
.api-service-item {
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

.item-method {
  flex-shrink: 0;
  width: 64px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
  border-radius: 4px;
  padding: 2px 0;
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

.item-path {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-family: monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-tag {
  flex-shrink: 0;
  font-size: 12px;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.item-workflow {
  display: flex;
  align-items: center;
  gap: 4px;

  img {
    width: 14px;
    height: 14px;
    object-fit: contain;
  }
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

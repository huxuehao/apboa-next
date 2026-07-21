<script setup lang="ts">
import type { Component } from 'vue'
import {
  ApiOutlined,
  CopyOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  HddOutlined,
  MessageOutlined,
  PoweroffOutlined
} from '@ant-design/icons-vue'
import type { WorkflowManagedResource, WorkflowResourceKind } from '@/types/workflowResources'

// 品牌图标导入 - 按资源类型匹配
import mysqlLogo from '@/assets/brand/mysql.png'
import oracleLogo from '@/assets/brand/oracle.png'
import postgresqlLogo from '@/assets/brand/postgresql.png'
import redisLogo from '@/assets/brand/redis.png'
import kafkaLogo from '@/assets/brand/kafka.png'
import rabbitmqLogo from '@/assets/brand/rabbitmq.png'
import rocketmqLogo from '@/assets/brand/rocketmq.png'

const props = defineProps<{
  kind: WorkflowResourceKind
  records: WorkflowManagedResource[]
}>()

const emit = defineEmits<{
  (e: 'edit', record: WorkflowManagedResource): void
  (e: 'copy', record: WorkflowManagedResource): void
  (e: 'remove', record: WorkflowManagedResource): void
  (e: 'toggle', record: WorkflowManagedResource, enabled: boolean): void
  (e: 'check', record: WorkflowManagedResource): void
}>()

/** 通用回退图标：按资源大类映射 */
const kindIconMap: Record<WorkflowResourceKind, Component> = {
  datasource: DatabaseOutlined,
  cache: HddOutlined,
  mq: MessageOutlined
}

const colorMap: Record<WorkflowResourceKind, string> = {
  datasource: '#2f54eb',
  cache: '#13a8a8',
  mq: '#722ed1'
}

/** 品牌图标映射：资源类型 → 品牌 PNG 图片 */
const brandIconMap: Record<string, string> = {
  MYSQL: mysqlLogo,
  ORACLE: oracleLogo,
  POSTGRESQL: postgresqlLogo,
  REDIS: redisLogo,
  KAFKA: kafkaLogo,
  RABBITMQ: rabbitmqLogo,
  ROCKETMQ: rocketmqLogo
}

/** 根据记录获取对应的品牌图标，无匹配则返回 null */
function getBrandIcon(record: WorkflowManagedResource): string | null {
  if (record.type && brandIconMap[record.type]) {
    return brandIconMap[record.type] ?? null
  }
  return null
}

function resourceAddress(record: WorkflowManagedResource) {
  if (props.kind === 'datasource') {
    const item = record as WorkflowManagedResource & { ip?: string; port?: string; db?: string }
    return [item.ip, item.port].filter(Boolean).join(':') + (item.db ? `/${item.db}` : '')
  }
  if (props.kind === 'cache') {
    const item = record as WorkflowManagedResource & { ip?: string; port?: number; db?: number }
    return `${[item.ip, item.port].filter(Boolean).join(':')} / DB ${item.db ?? 0}`
  }
  const item = record as WorkflowManagedResource & { address?: string; port?: number }
  return [item.address, item.port].filter(Boolean).join(':')
}

function resourceDescription(record: WorkflowManagedResource) {
  const parts = [
    record.type,
    resourceAddress(record),
    record.remark
  ].filter(Boolean)
  return parts.join(' · ') || '暂无描述'
}
</script>

<template>
  <div class="workflow-resource-list">
    <div
      v-for="record in records"
      :key="record.id"
      class="workflow-resource-list-item"
    >
      <div
        class="workflow-resource-avatar"
        :class="{ 'has-brand-icon': getBrandIcon(record) }"
        :style="getBrandIcon(record) ? {} : { backgroundColor: `${colorMap[kind]}14`, color: colorMap[kind] }"
      >
        <img v-if="getBrandIcon(record)" :src="getBrandIcon(record)!" class="workflow-resource-brand-icon" alt="" />
        <component :is="kindIconMap[kind]" v-else />
      </div>

      <div class="workflow-resource-info">
        <div class="workflow-resource-name-row">
          <span class="workflow-resource-name" :title="record.name">{{ record.name }}</span>
        </div>
        <div class="workflow-resource-meta" :title="resourceDescription(record)">
          {{ resourceDescription(record) }}
        </div>
      </div>

      <div class="workflow-resource-status">
        <ATooltip :title="record.enabled ? '点击禁用' : '点击启用'">
          <AButton
            type="text"
            size="small"
            class="workflow-resource-icon-btn"
            :class="{ active: record.enabled }"
            @click="emit('toggle', record, !record.enabled)"
          >
            <PoweroffOutlined />
          </AButton>
        </ATooltip>
      </div>

      <div class="workflow-resource-actions" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
        <ATooltip title="测试连接">
          <AButton type="text" size="small" class="workflow-resource-icon-btn" @click="emit('check', record)">
            <ApiOutlined />
          </AButton>
        </ATooltip>
        <ATooltip title="编辑">
          <AButton type="text" size="small" class="workflow-resource-icon-btn" @click="emit('edit', record)">
            <EditOutlined />
          </AButton>
        </ATooltip>
        <ATooltip title="复制">
          <AButton type="text" size="small" class="workflow-resource-icon-btn" @click="emit('copy', record)">
            <CopyOutlined />
          </AButton>
        </ATooltip>
        <ATooltip title="删除">
          <AButton type="text" size="small" danger class="workflow-resource-icon-btn" @click="emit('remove', record)">
            <DeleteOutlined />
          </AButton>
        </ATooltip>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/_settings.scss' as *;

.workflow-resource-list {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
}

.workflow-resource-list-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-extra-light);
  border-radius: 8px;
  transition: 0.1s;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: var(--color-bg-base, #f7f7f7);
  }
}

.workflow-resource-avatar {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--border-radius-lg);
  font-size: 18px;
  flex-shrink: 0;
  overflow: hidden;

  &.has-brand-icon {
    background: transparent;
  }
}

.workflow-resource-brand-icon {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.workflow-resource-info {
  min-width: 0;
  flex: 1;
}

.workflow-resource-name-row {
  display: flex;
  align-items: center;
  min-width: 0;
}

.workflow-resource-name {
  font-size: var(--font-size-base);
  font-weight: 500;
  color: var(--color-text-primary);
  display: block;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-resource-meta {
  font-size: var(--font-size-xs);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-placeholder);
}

.workflow-resource-status,
.workflow-resource-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 2px;
}

.workflow-resource-icon-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary, #666);

  &.active {
    color: #389e0d;
    background: rgba(82, 196, 26, 0.08);
  }
}
</style>

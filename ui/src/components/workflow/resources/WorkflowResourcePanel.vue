<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowReactive, watch } from 'vue'
import type { Component } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import * as workflowResourcesApi from '@/api/workflowResources'
import ApboaModal from '@/components/common/ApboaModal.vue'
import WorkflowResourceList from './WorkflowResourceList.vue'
import DatasourceForm from './forms/DatasourceForm.vue'
import CacheForm from './forms/CacheForm.vue'
import MqForm from './forms/MqForm.vue'
import ChannelForm from './forms/ChannelForm.vue'
import type {
  WorkflowManagedResource,
  WorkflowResourceKind,
  WorkflowResourceQuery
} from '@/types/workflowResources'

const props = defineProps<{
  kind: WorkflowResourceKind
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

type FormMode = 'add' | 'edit' | 'copy'
type ResourceFormExpose = {
  validate: () => Promise<void>
  getPayload: () => WorkflowManagedResource
}

const formComponents = shallowReactive<Record<WorkflowResourceKind, Component>>({
  datasource: DatasourceForm,
  cache: CacheForm,
  mq: MqForm,
  channel: ChannelForm
})

const meta = computed(() => {
  if (props.kind === 'datasource') {
    return {
      title: '数据源',
      addText: '新增数据源',
      searchPlaceholder: '搜索名称、主机、备注',
      emptyText: '暂无数据源',
      typeOptions: [
        { label: 'MySQL', value: 'MYSQL' },
        { label: 'Oracle', value: 'ORACLE' },
        { label: 'PostgreSQL', value: 'POSTGRESQL' }
      ]
    }
  }
  if (props.kind === 'cache') {
    return {
      title: 'Redis 缓存',
      addText: '新增缓存',
      searchPlaceholder: '搜索名称、主机、备注',
      emptyText: '暂无缓存',
      typeOptions: [{ label: 'Redis', value: 'REDIS' }]
    }
  }
  if (props.kind === 'channel') {
    return {
      title: '通知渠道',
      addText: '新增渠道',
      searchPlaceholder: '搜索名称、备注',
      emptyText: '暂无渠道',
      typeOptions: [
        { label: '邮箱（SMTP）', value: 'EMAIL' },
        { label: '企业微信机器人', value: 'WECOM' },
        { label: '钉钉机器人', value: 'DINGTALK' },
        { label: '飞书机器人', value: 'FEISHU' }
      ]
    }
  }
  return {
    title: '消息队列',
    addText: '新增消息',
    searchPlaceholder: '搜索名称、地址、备注',
    emptyText: '暂无消息资源',
    typeOptions: [
      { label: 'Kafka', value: 'KAFKA' },
      { label: 'RabbitMQ', value: 'RABBITMQ' },
      { label: 'RocketMQ', value: 'ROCKETMQ' }
    ]
  }
})

const loading = ref(false)
const records = ref<WorkflowManagedResource[]>([])
const total = ref(0)
const query = reactive<WorkflowResourceQuery>({
  page: 1,
  size: 500,
  name: undefined,
  type: undefined,
  enabled: undefined
})

const formVisible = ref(false)
const formMode = ref<FormMode>('add')
const formData = ref<Partial<WorkflowManagedResource>>({})
const formRef = ref<ResourceFormExpose>()
const formLoading = ref(false)
const checking = ref(false)

const modalTitle = computed(() => {
  const action = formMode.value === 'add' ? '新增' : formMode.value === 'copy' ? '复制' : '编辑'
  return `${action}${meta.value.title}`
})
const showPagination = computed(() => total.value > (query.size || 10))

async function load() {
  loading.value = true
  try {
    const res = await workflowResourcesApi.page(props.kind, { ...query })
    records.value = (res.data.data.records || []) as WorkflowManagedResource[]
    total.value = res.data.data.total || 0
  } finally {
    loading.value = false
  }
}

function resetQueryAndLoad() {
  query.page = 1
  load()
}

function handleAdd() {
  formMode.value = 'add'
  formData.value = {}
  formVisible.value = true
}

function handleEdit(record: WorkflowManagedResource) {
  formMode.value = 'edit'
  formData.value = { ...record, password: '' }
  formVisible.value = true
}

function handleCopy(record: WorkflowManagedResource) {
  formMode.value = 'copy'
  formData.value = {
    ...record,
    id: undefined,
    name: `${record.name || meta.value.title}副本`,
    password: ''
  }
  formVisible.value = true
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const payload = formRef.value?.getPayload()
  if (!payload) return

  formLoading.value = true
  try {
    if (formMode.value === 'edit') {
      await workflowResourcesApi.update(props.kind, payload)
    } else {
      await workflowResourcesApi.save(props.kind, payload)
    }
    message.success('保存成功')
    formVisible.value = false
    await afterChanged()
  } finally {
    formLoading.value = false
  }
}

async function handleFormCheck() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const payload = formRef.value?.getPayload()
  if (!payload) return

  checking.value = true
  try {
    await workflowResourcesApi.checkConnect(props.kind, payload)
    message.success('连接测试成功')
  } finally {
    checking.value = false
  }
}

async function handleSavedCheck(record: WorkflowManagedResource) {
  if (!record.id) return
  try {
    await workflowResourcesApi.checkSavedConnect(props.kind, record.id)
    message.success('连接测试成功')
  } finally {
    await afterChanged(false)
  }
}

async function handleToggle(record: WorkflowManagedResource, enabled: boolean) {
  if (!record.id) return
  await workflowResourcesApi.enable(props.kind, record.id, enabled)
  message.success(enabled ? '已启用' : '已禁用')
  await afterChanged()
}

function handleDelete(record: WorkflowManagedResource) {
  if (!record.id) return
  Modal.confirm({
    title: `删除${meta.value.title}`,
    content: `确定删除“${record.name}”吗？如果已被工作流引用，系统会阻止普通删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    icon: null,
    onOk: async () => {
      try {
        await workflowResourcesApi.remove(props.kind, [record.id!], 0)
        message.success('删除成功')
        await afterChanged()
      } catch (error) {
        const text = String(error || '')
        if (!text.includes('used by workflow')) {
          throw error
        }
        Modal.confirm({
          title: '资源正在被工作流使用',
          content: '继续删除会移除资源与工作流的绑定关系，相关节点需要重新配置资源。是否强制删除？',
          okText: '强制删除',
          okType: 'danger',
          cancelText: '取消',
          icon: null,
          onOk: async () => {
            await workflowResourcesApi.remove(props.kind, [record.id!], 1)
            message.success('删除成功')
            await afterChanged()
          }
        })
      }
    }
  })
}

async function afterChanged(reloadSummary = true) {
  await load()
  if (reloadSummary) emit('changed')
}

watch(() => props.kind, () => {
  query.page = 1
  query.name = undefined
  query.type = undefined
  query.enabled = undefined
  load()
})

onMounted(load)
</script>

<template>
  <div class="workflow-resource-panel">
    <div class="workflow-resource-header">
      <h2 class="settings-page-title m-0">{{ meta.title }}</h2>
    </div>

    <div class="workflow-resource-toolbar">
      <div class="workflow-resource-filters">
        <AInput
          v-model:value="query.name"
          :placeholder="meta.searchPlaceholder"
          style="width: 220px"
          allow-clear
          @pressEnter="resetQueryAndLoad"
        >
          <template #suffix>
            <SearchOutlined />
          </template>
        </AInput>
        <ASelect
          v-model:value="query.type"
          allow-clear
          placeholder="类型"
          style="width: 128px"
          :options="meta.typeOptions"
          @change="resetQueryAndLoad"
        />
        <ASelect
          v-model:value="query.enabled"
          allow-clear
          placeholder="启用状态"
          style="width: 128px"
          :options="[
            { label: '启用', value: true },
            { label: '禁用', value: false }
          ]"
          @change="resetQueryAndLoad"
        />
      </div>
      <div class="workflow-resource-create">
        <AButton type="primary" @click="handleAdd" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
          <PlusOutlined />
          {{ meta.addText }}
        </AButton>
      </div>
    </div>

    <div class="workflow-resource-content">
      <div class="workflow-resource-body">
        <WorkflowResourceList
          v-if="records.length > 0"
          :kind="kind"
          :records="records"
          @edit="handleEdit"
          @copy="handleCopy"
          @remove="handleDelete"
          @toggle="handleToggle"
          @check="handleSavedCheck"
        />
        <div v-else-if="!loading" class="workflow-resource-empty">
          <AEmpty :description="meta.emptyText" />
        </div>
        <div v-if="loading" class="workflow-resource-loading">
          <ApboaSpin :spinning="true" />
        </div>
      </div>
    </div>

    <div v-if="showPagination" class="workflow-resource-pagination">
      <APagination
        v-model:current="query.page"
        :page-size="query.size"
        :total="total"
        show-less-items
        @change="load"
      />
    </div>

    <ApboaModal
      v-model:open="formVisible"
      :title="modalTitle"
      :confirm-loading="formLoading"
      default-width="640px"
      expanded-width="860px"
      :destroyOnClose="true"
      @cancel="formVisible = false"
    >
      <component
        v-if="formVisible"
        ref="formRef"
        :is="formComponents[kind]"
        :initial-value="formData"
      />
      <template #footer>
        <div class="workflow-resource-modal-footer">
          <AButton :loading="checking" @click="handleFormCheck">测试连接</AButton>
          <div class="workflow-resource-modal-actions">
            <AButton @click="formVisible = false">取消</AButton>
            <AButton type="primary" :loading="formLoading" @click="handleSubmit">保存</AButton>
          </div>
        </div>
      </template>
    </ApboaModal>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/_settings.scss' as *;

.workflow-resource-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.workflow-resource-header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  margin-bottom: var(--spacing-md);
}

.workflow-resource-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  margin-bottom: var(--spacing-md);
}

.workflow-resource-filters {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.workflow-resource-create {
  margin-left: auto;
  flex-shrink: 0;
}

.workflow-resource-content {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.workflow-resource-body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workflow-resource-empty {
  flex: 1;
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.workflow-resource-loading {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.62);
}

.workflow-resource-pagination {
  display: flex;
  justify-content: center;
  flex-shrink: 0;
  padding-top: var(--spacing-md);
}

.workflow-resource-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.workflow-resource-modal-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
</style>

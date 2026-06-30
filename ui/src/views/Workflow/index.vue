/**
 * 工作流管理主页
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { onActivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { DatabaseOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { storeToRefs } from 'pinia'
import { useWorkflowStore } from '@/stores'
import * as workflowApi from '@/api/workflow'
import * as workflowResourcesApi from '@/api/workflowResources'
import WorkflowCard from '@/components/workflow/list/WorkflowCard.vue'
import WorkflowCreateCard from '@/components/workflow/list/WorkflowCreateCard.vue'
import WorkflowInfoModal from '@/components/workflow/list/WorkflowInfoModal.vue'
import WorkflowResourceModal from '@/components/workflow/resources/WorkflowResourceModal.vue'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'
import { createDefaultWorkflowDefinition } from '@/utils/workflow/defaultDefinition'
import type { Workflow } from '@/types/workflow'
import type { WorkflowResourceSummary } from '@/types/workflowResources'

const router = useRouter()
const store = useWorkflowStore()
const { workflows, loading, hasMore, listDirty } = storeToRefs(store)

const filterName = ref('')
const filterStatus = ref<'DRAFT' | 'PUBLISHED' | undefined>(undefined)
const filterEnabled = ref<boolean | undefined>(undefined)
const resourceModalVisible = ref(false)
const infoModalVisible = ref(false)
const infoModalMode = ref<'create' | 'edit'>('create')
const infoModalLoading = ref(false)
const editingWorkflow = ref<Workflow | null>(null)
const infoModalInitial = ref<{ name?: string; remark?: string }>({})
const resourceSummary = ref<WorkflowResourceSummary>({
  total: 0,
  datasourceTotal: 0,
  cacheTotal: 0,
  mqTotal: 0,
  datasourceEnabled: 0,
  cacheEnabled: 0,
  mqEnabled: 0,
})

/** 用于强制重建 InfiniteLoading 组件的 key */
const infiniteLoadingKey = ref(0)
/** 是否首次加载 */
const isFirstLoad = ref(true)

function resetListAndRebuild() {
  workflows.value = []
  store.resetPagination()
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

async function refreshListFromServer() {
  workflows.value = []
  store.resetPagination()
  isFirstLoad.value = false
  await store.fetchPage(1)
  infiniteLoadingKey.value++
}

function search() {
  store.query = {
    ...store.query,
    name: filterName.value || undefined,
    status: filterStatus.value,
    enabled: filterEnabled.value,
  }
  resetListAndRebuild()
}

async function loadResourceSummary() {
  const response = await workflowResourcesApi.summary()
  resourceSummary.value = response.data.data || resourceSummary.value
}

function handleResourceChanged(summary?: WorkflowResourceSummary) {
  if (summary) {
    resourceSummary.value = summary
    return
  }
  loadResourceSummary()
}

function openCreateWorkflow() {
  infoModalMode.value = 'create'
  editingWorkflow.value = null
  infoModalInitial.value = {}
  infoModalVisible.value = true
}

function editWorkflow(record: Workflow) {
  infoModalMode.value = 'edit'
  editingWorkflow.value = record
  infoModalInitial.value = {
    name: record.name,
    remark: record.remark,
  }
  infoModalVisible.value = true
}

function designWorkflow(record: Workflow) {
  if (record.id) router.push(`/workflow/${record.id}/edit`)
}

async function submitWorkflowInfo(payload: { name: string; remark?: string }) {
  infoModalLoading.value = true
  try {
    if (infoModalMode.value === 'create') {
      const response = await workflowApi.workflowSave({
        name: payload.name,
        remark: payload.remark,
        status: 'DRAFT',
        version: '0',
        locked: 0,
        enabled: true,
        config: createDefaultWorkflowDefinition(),
      })
      message.success('工作流已创建')
      infoModalVisible.value = false
      store.markListDirty()
      const id = response.data.data.id
      if (id) await router.push(`/workflow/${id}/edit`)
      return
    }

    if (!editingWorkflow.value?.id) return
    await workflowApi.workflowUpdate({
      ...editingWorkflow.value,
      name: payload.name,
      remark: payload.remark,
    })
    message.success('工作流信息已保存')
    infoModalVisible.value = false
    store.markListDirty()
    await refreshListFromServer()
    store.listDirty = false
  } finally {
    infoModalLoading.value = false
  }
}

async function copyWorkflow(record: Workflow) {
  if (!record.id) return
  await workflowApi.workflowCopy(record.id)
  message.success('工作流已复制')
  store.markListDirty()
  await refreshListFromServer()
  store.listDirty = false
}

async function removeWorkflow(record: Workflow) {
  if (!record.id) return
  Modal.confirm({
    title: '删除工作流',
    content: '删除后无法恢复；如果存在运行记录或资源绑定，后端会阻止普通删除。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await workflowApi.workflowRemove([record.id!], 0)
      message.success('删除成功')
      store.markListDirty()
      await refreshListFromServer()
      store.listDirty = false
    },
  })
}

async function handleInfiniteLoading($state: {
  loaded: () => void
  complete: () => void
  error: () => void
}) {
  if (isFirstLoad.value) {
    isFirstLoad.value = false
    if (workflows.value.length > 0) {
      $state.loaded()
      return
    }
    try {
      await store.fetchPage(1)
      if (hasMore.value) {
        $state.loaded()
      } else {
        $state.complete()
      }
    } catch {
      isFirstLoad.value = true
      $state.error()
    }
    return
  }

  if (!hasMore.value || loading.value) {
    $state.complete()
    return
  }

  try {
    await store.loadMore()
    if (hasMore.value) {
      $state.loaded()
    } else {
      $state.complete()
    }
  } catch {
    $state.error()
  }
}

watch([filterStatus, filterEnabled], () => {
  search()
})

async function refreshIfDirty() {
  if (!listDirty.value) return
  await refreshListFromServer()
  store.listDirty = false
}

onMounted(() => {
  refreshIfDirty()
  loadResourceSummary()
})

onActivated(refreshIfDirty)
</script>

<template>
  <div class="agent-page">
    <section class="intro-section">
      <h3 class="intro-title">工作流</h3>
      <p class="intro-desc text-secondary">
        工作流是智能体业务编排的核心引擎，通过可视化拖拽将开始、结束、逻辑判断、数据处理等节点自由组合，
        灵活接入缓存、数据源、消息队列等外部资源，实现节点间变量的精准传递与条件路由。
        调试运行让每一步执行状态一目了然，发布后生成不可变版本并绑定路由，
        将复杂的业务过程沉淀为可调用、可追踪、可复用的标准化流程。
      </p>
    </section>

    <section class="filter-section flex justify-between items-center">
      <div class="filter-left">
        <AInput
          v-model:value="filterName"
          allow-clear
          style="width: 320px; border: rgba(14,14,14,0.1) solid 1px !important;"
          placeholder="搜索工作流名称"
          @pressEnter="search()"
        >
          <template #suffix>
            <AButton type="text" size="small" @click="search()">
              <SearchOutlined />
            </AButton>
          </template>
        </AInput>
      </div>
      <div class="filter-right flex items-center gap-md">
        <ASelect
          v-model:value="filterStatus"
          allow-clear
          placeholder="发布状态"
          style="width: 140px; border: rgba(14,14,14,0.1) solid 1px !important; border-radius: 6px;"
          :options="[
            { label: '草稿', value: 'DRAFT' },
            { label: '已发布', value: 'PUBLISHED' },
          ]"
          @change="search()"
        />
        <ASelect
          v-model:value="filterEnabled"
          allow-clear
          placeholder="启用状态"
          style="width: 140px; border: rgba(14,14,14,0.1) solid 1px !important; border-radius: 6px;"
          :options="[
            { label: '启用', value: true },
            { label: '禁用', value: false },
          ]"
          @change="search()"
        />
        <ABadge :count="resourceSummary.total" :overflow-count="999">
          <AButton @click="resourceModalVisible = true">
            <DatabaseOutlined />
            资源维护
          </AButton>
        </ABadge>
      </div>
    </section>

    <section class="card-section">
      <div class="card-grid">
        <WorkflowCreateCard
          v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']"
          @create="openCreateWorkflow"
        />
        <WorkflowCard
          v-for="workflow in workflows"
          :key="workflow.id"
          :data="workflow"
          @edit="editWorkflow"
          @copy="copyWorkflow"
          @design="designWorkflow"
          @remove="removeWorkflow"
        />
      </div>

      <ApboaInfiniteLoading
        :loading-key="infiniteLoadingKey"
        @infinite="handleInfiniteLoading"
      />
    </section>

    <WorkflowInfoModal
      v-model:open="infoModalVisible"
      :mode="infoModalMode"
      :initial="infoModalInitial"
      :loading="infoModalLoading"
      @submit="submitWorkflowInfo"
    />

    <AModal
      v-model:open="resourceModalVisible"
      wrap-class-name="full-modal"
      :footer="null"
      :destroyOnClose="true"
      :width="'100%'"
    >
      <WorkflowResourceModal @changed="handleResourceChanged" />
    </AModal>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/agent/index.scss' as *;
</style>

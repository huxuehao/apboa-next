/**
 * 工作流管理主页面
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { storeToRefs } from 'pinia'
import { useWorkflowStore } from '@/stores'
import * as workflowApi from '@/api/workflow'
import WorkflowCard from '@/components/workflow/list/WorkflowCard.vue'
import WorkflowCreateCard from '@/components/workflow/list/WorkflowCreateCard.vue'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'
import { cloneDefaultConfig, cloneDefaultInputs, cloneDefaultOutputs, getWorkflowNodeSchema } from '@/config/workflow/nodeSchemas'
import type { Workflow, WorkflowDefinition, WorkflowNodeDefinition } from '@/types/workflow'

const router = useRouter()
const store = useWorkflowStore()
const { workflows, loading, hasMore } = storeToRefs(store)

const filterName = ref('')
const filterStatus = ref<'DRAFT' | 'PUBLISHED' | undefined>(undefined)
const filterEnabled = ref<boolean | undefined>(undefined)

/** 用于强制重建 InfiniteLoading 组件的 key */
const infiniteLoadingKey = ref(0)
/** 是否首次加载 */
const isFirstLoad = ref(true)

/**
 * 重置列表状态并重建 InfiniteLoading 组件
 */
function resetListAndRebuild() {
  workflows.value = []
  store.resetPagination()
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

/**
 * 执行搜索
 */
function search() {
  store.query.value = {
    ...store.query.value,
    name: filterName.value || undefined,
    status: filterStatus.value,
    enabled: filterEnabled.value,
  }
  resetListAndRebuild()
}

async function createWorkflow() {
  const response = await workflowApi.workflowSave({
    name: '未命名工作流',
    remark: '用于编排智能体业务流程',
    status: 'DRAFT',
    version: '0',
    locked: 0,
    enabled: true,
    config: defaultDefinition(),
  })
  message.success('工作流已创建')
  const id = response.data.data.id
  if (id) await router.push(`/workflow/${id}/edit`)
}

function editWorkflow(record: Workflow) {
  if (record.id) router.push(`/workflow/${record.id}/edit`)
}

async function copyWorkflow(record: Workflow) {
  if (!record.id) return
  const response = await workflowApi.workflowCopy(record.id)
  message.success('工作流已复制')
  await router.push(`/workflow/${response.data.data.id}/edit`)
}

async function publishWorkflow(record: Workflow) {
  if (!record.id) return
  Modal.confirm({
    title: '发布工作流',
    content: '发布会生成不可变版本，正式运行将使用最新发布版本。',
    okText: '发布',
    cancelText: '取消',
    onOk: async () => {
      await workflowApi.workflowPublish(record.id!)
      message.success('发布成功')
      resetListAndRebuild()
    },
  })
}

async function lockWorkflow(record: Workflow) {
  if (!record.id) return
  await workflowApi.workflowLock(record.id, record.locked ? 0 : 1)
  message.success(record.locked ? '已解锁' : '已锁定')
  resetListAndRebuild()
}

function debugWorkflow(record: Workflow) {
  editWorkflow(record)
}

async function removeWorkflow(record: Workflow) {
  if (!record.id) return
  Modal.confirm({
    title: '删除工作流',
    content: '删除后无法恢复；若存在运行记录或资源绑定，后端会阻止普通删除。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await workflowApi.workflowRemove([record.id!], 0)
      message.success('删除成功')
      resetListAndRebuild()
    },
  })
}

function defaultDefinition(): WorkflowDefinition {
  const start = createNode('start', 'START', { x: 120, y: 180 })
  const end = createNode('end', 'END', { x: 520, y: 180 })
  end.inputConfigs = [{ name: 'input', sourceType: 'NODE_OUTPUT', nodeId: 'start', outputName: 'output' }]
  return {
    nodes: [start, end],
    edges: [{ id: 'edge-start-end', source: 'start', target: 'end', sourceHandle: 'output', targetHandle: 'input', label: '' }],
    viewport: { x: 0, y: 0, zoom: 1 },
    metadata: { schemaVersion: '1.0', nodeVersion: '1.0', updatedAt: new Date().toISOString() },
  }
}

function createNode(id: string, type: string, position: { x: number; y: number }): WorkflowNodeDefinition {
  const schema = getWorkflowNodeSchema(type)!
  return {
    id,
    type,
    name: schema.title,
    position,
    config: cloneDefaultConfig(schema),
    inputConfigs: cloneDefaultInputs(schema),
    outputConfigs: cloneDefaultOutputs(schema, id),
    ui: {},
  }
}

/**
 * 处理无限加载
 *
 * @param $state 加载状态对象
 */
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

/**
 * 监听筛选条件变化，重置状态并重建 InfiniteLoading
 */
watch([filterStatus, filterEnabled], () => {
  search()
})
</script>

<template>
  <div class="agent-page">
    <section class="intro-section">
      <h3 class="intro-title">工作流</h3>
      <p class="intro-desc text-secondary">
        工作流是智能体业务编排的核心引擎，通过可视化拖拽将开始、结束、逻辑判断、数据处理等节点自由组合，
        灵活接入缓存、数据源、消息队列等外部资源，实现节点间变量的精准传递与条件路由。
        调试运行让每一步执行状态一目了然，发布后生成不可变版本并绑定路由，
        将复杂的业务过程真正沉淀为可调用、可追踪、可复用的标准化流程。
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
      </div>
    </section>

    <section class="card-section">
      <div class="card-grid">
        <WorkflowCreateCard
          v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']"
          @create="createWorkflow"
        />
        <WorkflowCard
          v-for="workflow in workflows"
          :key="workflow.id"
          :data="workflow"
          @edit="editWorkflow"
          @copy="copyWorkflow"
          @publish="publishWorkflow"
          @debug="debugWorkflow"
          @lock="lockWorkflow"
          @remove="removeWorkflow"
        />
      </div>

      <ApboaInfiniteLoading
        :loading-key="infiniteLoadingKey"
        @infinite="handleInfiniteLoading"
      />
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/agent/index.scss' as *;
</style>

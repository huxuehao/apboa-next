/**
 * MCP 服务管理主页面
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed, h, onActivated, onBeforeMount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Modal } from 'ant-design-vue'
import { CloudServerOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { storeToRefs } from 'pinia'
import { McpActivationStatus, McpFailureSource } from '@/types'
import type { McpProtocol, McpServerVO } from '@/types'
import * as mcpApi from '@/api/mcp'
import { useMcpStore } from '@/stores'
import { ApboaModalApi } from '@/components/common/ApboaModalApi.ts'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'
import CreateCard from '@/components/mcp/CreateCard.vue'
import McpCard from '@/components/mcp/McpCard.vue'
import McpForm from '@/components/mcp/McpForm.vue'
import { getMcpConnectionStatusText } from '@/composables/useMcpPresentation'

defineOptions({ name: 'McpManagementView' })

const store = useMcpStore()
const router = useRouter()
const { list, selectedProtocol, keyword, loading, hasMore } = storeToRefs(store)

const formVisible = ref(false)
const currentData = ref<McpServerVO | undefined>(undefined)
const initialProtocol = ref<McpProtocol | undefined>(undefined)
const infiniteLoadingKey = ref(0)
const isFirstLoad = ref(true)
const hasActivatedOnce = ref(false)

const currentProtocol = computed<McpProtocol | null>(() => selectedProtocol.value as McpProtocol | null)

const protocolOptions = [
  { label: '全部', value: null },
  { label: 'HTTP', value: 'HTTP' },
  { label: 'SSE', value: 'SSE' },
  { label: 'STDIO', value: 'STDIO' }
]

function connectionText(data: McpServerVO) {
  return getMcpConnectionStatusText(data)
}

function handleCreate(protocol: McpProtocol) {
  currentData.value = undefined
  initialProtocol.value = protocol
  formVisible.value = true
}

async function handleView(id: string) {
  const response = await mcpApi.detail(id)
  const data = response.data.data

  ApboaModalApi.open({
    title: 'MCP 服务详情',
    titleIcon: CloudServerOutlined,
    footer: null,
    content: h('div', {}, [
      h('p', {}, [h('strong', '关联智能体: '), data.used?.length ? data.used.join('、') : '无']),
      h('p', {}, [h('strong', '名称: '), data.name]),
      h('p', {}, [h('strong', '协议: '), data.protocol]),
      h('p', {}, [h('strong', '运行模式: '), data.mode === 'SYNC' ? '同步' : '异步']),
      h('p', {}, [h('strong', '是否启用: '), data.enabled ? '是' : '否']),
      h('p', {}, [h('strong', '超时时间: '), `${data.timeout}ms`]),
      h('p', {}, [h('strong', '自动降级失败次数: '), String(data.runtimeFailThreshold ?? 3)]),
      h('p', {}, [h('strong', '描述: '), data.description]),
      h('p', {}, [h('strong', '连接状态: '), connectionText(data)]),
      h('p', {}, [h('strong', '状态说明: '), data.activationMessage || '无']),
      h('p', {}, [h('strong', '失败来源: '), data.failureSource === McpFailureSource.RUNTIME_AUTO_DEGRADE ? '运行时自动降级' : '无']),
      h('p', {}, [h('strong', '工具总数: '), String(data.toolCount || 0)]),
      h('p', {}, [h('strong', '全局可用工具: '), String(data.availableToolCount || 0)]),
      h('p', {}, [h('strong', '待刷新: '), data.needsSync ? '是' : '否']),
      ...(data.activationStatusChangedAt ? [h('p', {}, [h('strong', '连接状态变更时间: '), data.activationStatusChangedAt])] : []),
      ...(data.lastActivationTime ? [h('p', {}, [h('strong', '上次连接时间: '), data.lastActivationTime])] : []),
      ...(data.lastToolSyncTime ? [h('p', {}, [h('strong', '上次工具刷新时间: '), data.lastToolSyncTime])] : []),
      h('p', {}, [h('strong', '健康状态: '), data.healthStatus]),
      ...(data.lastHealthCheck ? [h('p', {}, [h('strong', '上次健康检查时间: '), data.lastHealthCheck])] : []),
      h('p', {}, h('strong', '协议配置:')),
      h('pre', {
        style: {
          background: '#f5f5f5',
          padding: '12px',
          borderRadius: '4px'
        }
      }, JSON.stringify(data.protocolConfig, null, 2))
    ])
  })
}

async function handleEdit(id: string) {
  const response = await mcpApi.detail(id)
  currentData.value = response.data.data
  initialProtocol.value = undefined
  formVisible.value = true
}

function resetListAndRebuild() {
  list.value = []
  store.resetPagination()
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

function resetListState() {
  list.value = []
  store.resetPagination()
  isFirstLoad.value = true
}

async function handleDelete(id: string) {
  const used = await store.checkUsedWithAgent(id)
  if (used.length > 0) {
    Modal.confirm({
      title: '二次确认',
      content: `该 MCP 服务正在被 [ ${used.join('、')} ] 智能体引用，删除后可能会影响上述智能体的正常使用。`,
      okText: '确认并继续删除',
      onOk: async () => {
        await store.deleteConfig(id)
        resetListAndRebuild()
      }
    })
    return
  }

  Modal.confirm({
    title: '确认删除',
    content: '删除后无法恢复，是否继续？',
    onOk: async () => {
      await store.deleteConfig(id)
      resetListAndRebuild()
    }
  })
}

function handleFormSuccess() {
  resetListAndRebuild()
}

function handleSearch() {
  store.setKeyword(keyword.value)
}

async function handleEnable(id: string) {
  const response = await mcpApi.detail(id)
  const { enabled } = response.data.data

  const used = await store.checkUsedWithAgent(id)
  if (used.length > 0 && enabled) {
    Modal.confirm({
      title: '二次确认',
      content: `该 MCP 服务正在被 [ ${used.join('、')} ] 智能体引用，停用后可能会影响上述智能体的正常使用。`,
      okText: '确认并继续',
      onOk: async () => {
        await store.toggleEnabled(id, !enabled)
      }
    })
    return
  }

  await store.toggleEnabled(id, !enabled)
}

async function handleActivate(id: string) {
  const item = list.value.find(server => server.id === id)
  const actionLabel = item && item.activationStatus === McpActivationStatus.FAILED ? '重试连接' : '连接'
  await store.activateServer(id, actionLabel)
}

async function handleSync(id: string) {
  await store.syncServerTools(id, '刷新工具')
}

/**
 * 处理工具治理 - 跳转到工具治理页面
 */
function handleToolGovernance(id: string) {
  router.push(`/mcp/${id}/tools`)
}

async function handleInfiniteLoading($state: {
  loaded: () => void
  complete: () => void
  error: () => void
}) {
  if (isFirstLoad.value) {
    isFirstLoad.value = false
    if (list.value.length > 0) {
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

watch([selectedProtocol, keyword], () => {
  resetListAndRebuild()
})

onBeforeMount(() => {
  resetListState()
})

onActivated(() => {
  if (!hasActivatedOnce.value) {
    hasActivatedOnce.value = true
    return
  }
  resetListAndRebuild()
})
</script>

<template>
  <div class="mcp-page">
    <section class="intro-section">
      <h3 class="intro-title">MCP</h3>
      <p class="intro-desc text-secondary">
        启用决定这个 MCP 是否允许参与系统；连接用于校验连通性并加载工具目录；刷新工具只在当前配置基础上重新获取工具目录。
      </p>
    </section>

    <section class="filter-section flex justify-between items-center">
      <div class="filter-left">
        <ASegmented
          v-model:value="selectedProtocol"
          :options="protocolOptions"
        />
      </div>

      <div class="filter-right">
        <AInput
          v-model:value="keyword"
          placeholder="搜索 MCP 服务名称"
          style="width: 300px;"
          @pressEnter="handleSearch"
        >
          <template #suffix>
            <AButton type="text" size="small" @click="handleSearch">
              <SearchOutlined />
            </AButton>
          </template>
        </AInput>
      </div>
    </section>

    <section class="card-section">
      <div class="card-grid">
        <CreateCard :protocol="currentProtocol" @create="handleCreate" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']" />

        <McpCard
          v-for="item in list"
          :key="item.id"
          :data="item"
          @view="handleView"
          @edit="handleEdit"
          @activate="handleActivate"
          @sync="handleSync"
          @toolGovernance="handleToolGovernance"
          @enable="handleEnable"
          @delete="handleDelete"
        />
      </div>

      <ApboaInfiniteLoading
        :loading-key="infiniteLoadingKey"
        @infinite="handleInfiniteLoading"
      />
    </section>

    <McpForm
      v-model:visible="formVisible"
      :data="currentData"
      :initial-protocol="initialProtocol"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/mcp/index.scss' as *;
</style>

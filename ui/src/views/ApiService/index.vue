/**
 * API服务主页面
 * 将已发布工作流通过网关暴露为API服务，支持按分类、应用筛选
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, SearchOutlined, AppstoreOutlined, KeyOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import type { GatewayApi, GatewayApp } from '@/types/apiService'
import * as apiServiceApi from '@/api/apiService'
import ApiServiceItem from '@/components/api-service/ApiServiceItem.vue'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'

const router = useRouter()

const list = ref<GatewayApi[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const isFirstLoad = ref(true)
const infiniteLoadingKey = ref(0)

const categories = ref<string[]>([])
const apps = ref<GatewayApp[]>([])
const selectedCategory = ref<string | null>(null)
const selectedAppId = ref<string | null>(null)
const keyword = ref('')

/**
 * 获取列表数据
 */
async function fetchPage(page: number) {
  loading.value = true
  try {
    const res = await apiServiceApi.pageApis({
      page,
      size: 50,
      name: keyword.value || undefined,
      category: selectedCategory.value || undefined,
      appId: selectedAppId.value || undefined
    })
    const data = res.data.data
    if (page === 1) {
      list.value = data.records || []
    } else {
      list.value = [...list.value, ...(data.records || [])]
    }
    hasMore.value = list.value.length < (data.total || 0)
    currentPage.value = page
  } catch (e) {
    console.error('加载列表失败:', e)
  } finally {
    loading.value = false
  }
}

/**
 * 无限滚动加载
 */
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
      await fetchPage(1)
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
    await fetchPage(currentPage.value + 1)
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
 * 无闪烁刷新：直接用新数据覆盖，不清空列表
 */
async function refreshWithoutClear() {
  try {
    const res = await apiServiceApi.pageApis({
      page: 1,
      size: 50,
      name: keyword.value || undefined,
      category: selectedCategory.value || undefined,
      appId: selectedAppId.value || undefined
    })
    const data = res.data.data
    list.value = data.records || []
    hasMore.value = list.value.length < (data.total || 0)
    currentPage.value = 1
    isFirstLoad.value = false
    infiniteLoadingKey.value++
  } catch (e) {
    console.error('刷新列表失败:', e)
  }
}

/**
 * 重置列表
 */
function resetListAndRebuild() {
  list.value = []
  currentPage.value = 1
  hasMore.value = true
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

/**
 * 加载筛选数据源（分类与应用）
 */
async function loadFilters() {
  try {
    const [categoryRes, appRes] = await Promise.all([
      apiServiceApi.getCategories(),
      apiServiceApi.listApps()
    ])
    categories.value = categoryRes.data.data || []
    apps.value = appRes.data.data || []
  } catch (e) {
    console.error('加载筛选数据失败:', e)
  }
}

function handleSearch() {
  resetListAndRebuild()
}

function handleCreate() {
  router.push('/api-service/new')
}

function handleEdit(api: GatewayApi) {
  router.push(`/api-service/${api.id}/edit`)
}

function handleRefresh() {
  refreshWithoutClear()
  loadFilters()
}

function goApps() {
  router.push('/api-service/apps')
}

function goClients() {
  router.push('/api-service/clients')
}

function goLogs() {
  router.push('/api-service/logs')
}

// 监听筛选条件变化
watch([selectedCategory, selectedAppId], () => {
  resetListAndRebuild()
})

onMounted(() => {
  loadFilters()
})
</script>

<template>
  <div class="api-service-page">
    <!-- 页面标题区 -->
    <section class="intro-section">
      <h3 class="intro-title">API服务</h3>
      <p class="intro-desc text-secondary">
        API服务模块基于异步非阻塞网关，将已发布的工作流暴露为标准HTTP API。<br/>
        通过网关应用管理服务端口，通过访问客户端与Token体系控制调用权限，请求参数将自动转换为工作流的输入参数。
      </p>
    </section>

    <!-- 筛选操作栏 -->
    <section class="filter-section flex justify-between items-center">
      <div class="filter-left flex items-center gap-md">
        <ASelect
          v-model:value="selectedAppId"
          placeholder="选择应用"
          style="width: 180px;"
          allow-clear
        >
          <ASelectOption v-for="app in apps" :key="app.id" :value="app.id">
            {{ app.name }}（:{{ app.port }}）
          </ASelectOption>
        </ASelect>

        <ASelect
          v-model:value="selectedCategory"
          placeholder="选择分类"
          style="width: 160px;"
          allow-clear
        >
          <ASelectOption v-for="cat in categories" :key="cat" :value="cat">
            {{ cat }}
          </ASelectOption>
        </ASelect>
      </div>

      <div class="filter-right flex items-center gap-md">
        <AInput
          v-model:value="keyword"
          placeholder="搜索API名称"
          style="width: 260px;"
          @pressEnter="handleSearch"
        >
          <template #suffix>
            <AButton type="text" size="small" @click="handleSearch">
              <SearchOutlined />
            </AButton>
          </template>
        </AInput>

        <ATooltip title="访问日志">
          <AButton @click="goLogs">
            <template #icon><FileTextOutlined /></template>
          </AButton>
        </ATooltip>

        <AButton @click="goApps">
          <template #icon><AppstoreOutlined /></template>
          网关应用
        </AButton>

        <AButton @click="goClients">
          <template #icon><KeyOutlined /></template>
          访问客户端
        </AButton>

        <AButton
          type="primary"
          v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']"
          @click="handleCreate"
        >
          <template #icon><PlusOutlined /></template>
          新增API
        </AButton>
      </div>
    </section>

    <!-- API列表区 -->
    <section class="list-section">
      <div v-if="list.length === 0 && !loading && !isFirstLoad" class="list-empty">
        <AEmpty description="暂无API服务" />
      </div>

      <div v-else class="list-container">
        <ApiServiceItem
          v-for="item in list"
          :key="item.id"
          :data="item"
          @edit="handleEdit"
          @refresh="handleRefresh"
        />

        <ApboaInfiniteLoading
          :loading-key="infiniteLoadingKey"
          @infinite="handleInfiniteLoading"
        />
      </div>
    </section>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ApiServiceView'
}
</script>

<style scoped lang="scss">
@use '@/styles/api-service/index.scss' as *;
</style>

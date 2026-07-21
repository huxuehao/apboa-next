/**
 * 自动化任务管理主页面
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import type { JobInfo } from '@/types'
import * as automationApi from '@/api/automation'
import AutomationItem from '@/components/automation/AutomationItem.vue'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'

const router = useRouter()

const list = ref<JobInfo[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const isFirstLoad = ref(true)
const infiniteLoadingKey = ref(0)

const filterType = ref<'AGENT' | 'WORKFLOW' | null>(null)
const keyword = ref('')

const typeOptions = [
  { label: '全部', value: null },
  { label: '智能体', value: 'AGENT' },
  { label: '工作流', value: 'WORKFLOW' }
]

/**
 * 获取列表数据
 */
async function fetchPage(page: number) {
  loading.value = true
  try {
    const res = await automationApi.pageJobs({
      page,
      size: 50,
      type: filterType.value,
      keyword: keyword.value || undefined
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
    const res = await automationApi.pageJobs({
      page: 1,
      size: 50,
      type: filterType.value,
      keyword: keyword.value || undefined
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
 * 搜索
 */
function handleSearch() {
  resetListAndRebuild()
}

/**
 * 新增
 */
function handleCreate() {
  router.push('/automation/new')
}

/**
 * 编辑
 */
function handleEdit(job: JobInfo) {
  router.push(`/automation/${job.id}/edit`)
}

/**
 * 刷新列表（无闪烁，直接覆盖数据）
 */
function handleRefresh() {
  refreshWithoutClear()
}

// 监听筛选条件变化
watch([filterType], () => {
  resetListAndRebuild()
})

onMounted(() => {
  // 初始化加载
})
</script>

<template>
  <div class="automation-page">
    <!-- 页面标题区 -->
    <section class="intro-section">
      <h3 class="intro-title">自动化</h3>
      <p class="intro-desc text-secondary">
        自动化模块让您可以为智能体和工作流设置定时执行任务，实现无人值守的自动化工作流。<br/>
        支持灵活的 Cron 表达式配置，满足各种调度需求。
      </p>
    </section>

    <!-- 筛选操作栏 -->
    <section class="filter-section flex justify-between items-center">
      <div class="filter-left">
        <ASegmented
          v-model:value="filterType"
          :options="typeOptions"
        />
      </div>

      <div class="filter-right flex items-center gap-md">
        <AInput
          v-model:value="keyword"
          placeholder="搜索任务名称"
          style="width: 300px;"
          @pressEnter="handleSearch"
        >
          <template #suffix>
            <AButton type="text" size="small" @click="handleSearch">
              <SearchOutlined />
            </AButton>
          </template>
        </AInput>

        <AButton
          type="primary"
          v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']"
          @click="handleCreate"
        >
          <template #icon><PlusOutlined /></template>
          新增任务
        </AButton>
      </div>
    </section>

    <!-- 任务列表区 -->
    <section class="list-section">
      <div v-if="list.length === 0 && !loading && !isFirstLoad" class="list-empty">
        <AEmpty description="暂无自动化任务" />
      </div>

      <div v-else class="list-container">
        <AutomationItem
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
  name: 'AutomationView'
}
</script>

<style scoped lang="scss">
@use '@/styles/automation/index.scss' as *;
</style>

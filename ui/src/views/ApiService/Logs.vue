/**
 * API访问日志页面
 * 展示网关API的访问记录，可查看请求参数、响应体、关联工作流运行
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LeftOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { GatewayApi, GatewayAccessLog } from '@/types/apiService'
import * as apiServiceApi from '@/api/apiService'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'

const router = useRouter()

const list = ref<GatewayAccessLog[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const isFirstLoad = ref(true)
const infiniteLoadingKey = ref(0)

const apis = ref<GatewayApi[]>([])
const selectedApiId = ref<string | null>(null)
const selectedStatus = ref<number | null>(null)

// 详情抽屉
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<GatewayAccessLog | null>(null)

const statusOptions = [
  { label: '全部', value: null },
  { label: '成功', value: 1 },
  { label: '失败', value: 0 }
]

async function fetchPage(page: number) {
  loading.value = true
  try {
    const res = await apiServiceApi.pageAccessLogs({
      page,
      size: 50,
      apiId: selectedApiId.value || undefined,
      status: selectedStatus.value ?? undefined
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
    console.error('加载日志失败:', e)
  } finally {
    loading.value = false
  }
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
      await fetchPage(1)
      hasMore.value ? $state.loaded() : $state.complete()
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
    hasMore.value ? $state.loaded() : $state.complete()
  } catch {
    $state.error()
  }
}

function resetListAndRebuild() {
  list.value = []
  currentPage.value = 1
  hasMore.value = true
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

async function loadApis() {
  try {
    const res = await apiServiceApi.getBriefApis()
    apis.value = res.data.data || []
  } catch (e) {
    console.error('加载API列表失败:', e)
  }
}

/**
 * 查看日志详情
 */
async function handleDetail(log: GatewayAccessLog) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await apiServiceApi.getAccessLog(log.id)
    detail.value = res.data.data
  } catch (e) {
    console.error('加载日志详情失败:', e)
  } finally {
    detailLoading.value = false
  }
}

/**
 * 计算耗时
 */
function duration(log: GatewayAccessLog): string {
  if (!log.startTime || !log.endTime) return '-'
  return `${log.endTime - log.startTime} ms`
}

/**
 * 格式化JSON文本用于展示
 */
function prettyJson(text?: string): string {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

function methodColor(method?: string): string {
  switch (method) {
    case 'GET': return '#2f9e44'
    case 'POST': return '#1971c2'
    case 'PUT': return '#e8590c'
    case 'DELETE': return '#e03131'
    case 'PATCH': return '#9c36b5'
    default: return '#495057'
  }
}

function handleBack() {
  router.push('/api-service')
}

onMounted(() => {
  loadApis()
})
</script>

<template>
  <div class="api-logs-page">
    <section class="intro-section">
      <span class="back-link" @click="handleBack">
        <LeftOutlined />
        返回API服务
      </span>
      <div class="intro-header">
        <h3 class="intro-title">API访问日志</h3>
      </div>
      <p class="intro-desc text-secondary">
        记录每一次API调用的请求参数、响应结果、访问IP与关联的工作流运行，便于问题排查与调用审计。
      </p>
    </section>

    <section class="filter-section flex items-center gap-md">
      <ASelect
        v-model:value="selectedApiId"
        placeholder="选择API"
        style="width: 280px;"
        allow-clear
        show-search
        option-filter-prop="label"
        @change="resetListAndRebuild"
      >
        <ASelectOption v-for="api in apis" :key="api.id" :value="api.id" :label="api.name">
          {{ api.name }}（{{ api.method }} {{ api.path }}）
        </ASelectOption>
      </ASelect>
      <ASegmented v-model:value="selectedStatus" :options="statusOptions" @change="resetListAndRebuild" />
    </section>

    <section class="list-section">
      <div v-if="list.length === 0 && !loading && !isFirstLoad" class="list-empty">
        <AEmpty description="暂无访问日志" />
      </div>
      <div v-else class="list-container">
        <div v-for="log in list" :key="log.id" class="log-item" @click="handleDetail(log)">
          <div class="log-main">
            <div class="log-path">{{ log.method }} <span>·</span> {{ log.path }}</div>
            <div class="log-meta">
              <span class="log-ip">{{ log.accessIp || '-' }}</span>
              <span>·</span>
              <span>{{ duration(log) }}</span>
              <span>·</span>
              <span>{{ log.createdAt }}</span>
            </div>
          </div>
          <div class="log-status">
            <ATag :color="log.status === 1 ? 'processing' : 'error'" :bordered="false">
              {{ log.httpStatus }} {{ log.status === 1 ? '成功' : '失败' }}
            </ATag>
          </div>
        </div>

        <ApboaInfiniteLoading
          :loading-key="infiniteLoadingKey"
          @infinite="handleInfiniteLoading"
        />
      </div>
    </section>

    <!-- 日志详情抽屉 -->
    <ADrawer
      v-model:open="detailVisible"
      title="访问日志详情"
      width="640"
      destroyOnClose
    >
      <ApboaSpin :spinning="detailLoading">
        <div v-if="detail" class="log-detail">
          <ADescriptions :column="1" bordered size="small">
            <ADescriptionsItem label="请求方法">{{ detail.method }}</ADescriptionsItem>
            <ADescriptionsItem label="请求路径">{{ detail.path }}</ADescriptionsItem>
            <ADescriptionsItem label="访问IP">{{ detail.accessIp || '-' }}</ADescriptionsItem>
            <ADescriptionsItem label="状态">
              <ATag :color="detail.status === 1 ? 'processing' : 'error'" :bordered="false">
                {{ detail.httpStatus }} {{ detail.status === 1 ? '成功' : '失败' }}
              </ATag>
            </ADescriptionsItem>
            <ADescriptionsItem label="耗时">{{ duration(detail) }}</ADescriptionsItem>
            <ADescriptionsItem label="工作流运行ID">{{ detail.workflowRunId || '-' }}</ADescriptionsItem>
          </ADescriptions>

          <div v-if="detail.error" class="detail-block">
            <div class="detail-label">错误信息</div>
            <pre class="detail-error">{{ detail.error }}</pre>
          </div>

          <div class="detail-block" v-if="detail.pathParams">
            <div class="detail-label">Path参数</div>
            <pre class="detail-code">{{ prettyJson(detail.pathParams) }}</pre>
          </div>
          <div class="detail-block" v-if="detail.queryParams">
            <div class="detail-label">Query参数</div>
            <pre class="detail-code">{{ prettyJson(detail.queryParams) }}</pre>
          </div>
          <div class="detail-block" v-if="detail.headerParams">
            <div class="detail-label">Header参数</div>
            <pre class="detail-code">{{ prettyJson(detail.headerParams) }}</pre>
          </div>
          <div class="detail-block" v-if="detail.requestBody">
            <div class="detail-label">请求体</div>
            <pre class="detail-code">{{ prettyJson(detail.requestBody) }}</pre>
          </div>
          <div class="detail-block" v-if="detail.responseBody">
            <div class="detail-label">响应体</div>
            <pre class="detail-code">{{ prettyJson(detail.responseBody) }}</pre>
          </div>
        </div>
      </ApboaSpin>
    </ADrawer>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ApiServiceLogs'
}
</script>

<style scoped lang="scss">
@use '@/styles/api-service/manage.scss' as *;

.filter-section {
  margin-bottom: var(--spacing-lg);
  gap: var(--spacing-md);
  position: sticky;
  top: 0;
  background-color: var(--color-bg);
  padding: var(--spacing-base) 0;
  z-index: 10;
}

.log-item {
  display: flex;
  align-items: center;
  padding: 14px 20px;
  border: 1px solid #EBEBEB;
  border-radius: 8px;
  gap: 16px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: background-color 0.2s ease;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02);
  }
}

.log-method {
  flex-shrink: 0;
  width: 50px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  border-radius: 4px;
  padding: 2px 0;
}

.log-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.log-path {
  font-size: 13px;
  // font-family: monospace;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

// .log-ip {
//   font-family: monospace;
// }

.log-status {
  flex-shrink: 0;
}

.log-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}

.detail-code,
.detail-error {
  margin: 0;
  padding: 12px;
  background-color: #F2F4F7;
  border-radius: 6px;
  font-size: 12px;
  // font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
}

.detail-error {
  color: #e03131;
}
</style>

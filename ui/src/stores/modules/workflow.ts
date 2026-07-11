/**
 * 工作流状态管理
 *
 * @author huxuehao
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as workflowApi from '@/api/workflow'
import type { PageResult } from '@/types/common'
import type {
  NodeMetadata,
  Workflow,
  WorkflowQuery,
  WorkflowRunResult,
  WorkflowValidationResult,
} from '@/types/workflow'

export const useWorkflowStore = defineStore('workflow', () => {
  const workflows = ref<Workflow[]>([])
  const metadata = ref<NodeMetadata[]>([])
  const loading = ref(false)
  const hasMore = ref(true)
  const currentPage = ref(1)
  const pageSize = ref(50)
  const total = ref(0)
  const listDirty = ref(false)
  const query = ref<WorkflowQuery>({ page: 1, size: 50 })
  const lastValidation = ref<WorkflowValidationResult | null>(null)
  const lastRun = ref<WorkflowRunResult | null>(null)

  const metadataByType = computed(() => {
    return metadata.value.reduce<Record<string, NodeMetadata>>((acc, item) => {
      acc[item.type] = item
      return acc
    }, {})
  })

  /**
   * 加载分页数据
   *
   * @param page 页码
   */
  async function fetchPage(page: number) {
    if (loading.value) return

    loading.value = true
    try {
      query.value = { ...query.value, page, size: pageSize.value }
      const response = await workflowApi.workflowPage(query.value)
      const result: PageResult<Workflow> = response.data.data

      if (page === 1) {
        workflows.value = result.records || []
      } else {
        workflows.value.push(...(result.records || []))
      }

      hasMore.value = workflows.value.length < result.total
      currentPage.value = page
      total.value = result.total || 0
    } catch (error) {
      console.error('加载工作流数据失败:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  async function refreshFirstPage() {
    resetPagination()
    await fetchPage(1)
    listDirty.value = false
  }

  function markListDirty() {
    listDirty.value = true
  }

  function upsertWorkflow(workflow: Workflow) {
    if (!workflow.id) return
    const index = workflows.value.findIndex((item) => item.id === workflow.id)
    if (index >= 0) {
      workflows.value[index] = { ...workflows.value[index], ...workflow }
    } else {
      workflows.value.unshift(workflow)
      total.value += 1
    }
  }

  /**
   * 加载更多数据
   */
  async function loadMore() {
    if (!hasMore.value || loading.value) return
    await fetchPage(currentPage.value + 1)
  }

  /**
   * 重置分页状态（不加载数据）
   */
  function resetPagination() {
    currentPage.value = 1
    hasMore.value = true
  }

  async function fetchMetadata() {
    if (metadata.value.length > 0) return metadata.value
    const response = await workflowApi.workflowNodeMetadata()
    metadata.value = response.data.data || []
    return metadata.value
  }

  async function validate(id: string) {
    const response = await workflowApi.workflowValidate(id)
    lastValidation.value = response.data.data
    return lastValidation.value
  }

  async function debugRun(id: string, payload = {}) {
    const response = await workflowApi.workflowDebugRun(id, payload)
    lastRun.value = response.data.data
    return lastRun.value
  }

  return {
    workflows,
    metadata,
    metadataByType,
    loading,
    hasMore,
    total,
    listDirty,
    query,
    lastValidation,
    lastRun,
    fetchPage,
    refreshFirstPage,
    loadMore,
    resetPagination,
    markListDirty,
    upsertWorkflow,
    fetchMetadata,
    validate,
    debugRun,
  }
})

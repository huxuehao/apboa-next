/**
 * 数据集取数组合式：调用面板取数接口，暴露结果/加载/错误状态。
 *
 * @author huxuehao
 */
import { ref } from 'vue'
import { datasetQuery } from '@/api/dashboard'
import type { DatasetExecuteResult } from '@/types/dashboard'

export function useDatasetData() {
  const result = ref<DatasetExecuteResult | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  /**
   * 按数据集 ID 取数
   */
  async function load(datasetId?: string | null, params?: Record<string, unknown>, limit?: number) {
    if (!datasetId) {
      result.value = null
      return
    }
    loading.value = true
    error.value = null
    try {
      const resp = await datasetQuery(datasetId, { params, limit })
      result.value = resp.data.data
    } catch (e: unknown) {
      error.value = typeof e === 'string' ? e : (e as Error)?.message || '取数失败'
    } finally {
      loading.value = false
    }
  }

  return { result, loading, error, load }
}

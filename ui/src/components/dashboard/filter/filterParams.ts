/**
 * 全局筛选器工具：初始化默认值、将筛选值归一化为数据集命名参数。
 *
 * @author huxuehao
 */
import dayjs, { type Dayjs } from 'dayjs'
import type { DashboardFilter } from '@/types/dashboard'

/** 筛选值集合，按 filter.id 存储控件原始值 */
export type FilterValues = Record<string, unknown>

/** 初始化筛选值（应用各筛选器默认值） */
export function initFilterValues(filters?: DashboardFilter[]): FilterValues {
  const values: FilterValues = {}
  ;(filters || []).forEach((f) => {
    if (f.default !== undefined) {
      values[f.id] = f.default
    }
  })
  return values
}

/**
 * 将筛选值转为数据集命名参数：
 * - dateRange → <paramKey>Start / <paramKey>End（YYYY-MM-DD 字符串）
 * - select / text → <paramKey>
 */
export function buildFilterParams(
  filters: DashboardFilter[] | undefined,
  values: FilterValues,
): Record<string, unknown> {
  const params: Record<string, unknown> = {}
  ;(filters || []).forEach((f) => {
    const v = values[f.id]
    if (f.type === 'dateRange') {
      if (Array.isArray(v) && v.length === 2 && v[0] && v[1]) {
        params[`${f.paramKey}Start`] = formatDate(v[0] as Dayjs | string)
        params[`${f.paramKey}End`] = formatDate(v[1] as Dayjs | string)
      }
    } else if (v !== undefined && v !== null && v !== '') {
      params[f.paramKey] = v
    }
  })
  return params
}

function formatDate(v: Dayjs | string): string {
  return dayjs(v).format('YYYY-MM-DD')
}

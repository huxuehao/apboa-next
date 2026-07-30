/**
 * 面板私有筛选器工具：初始化默认值、将筛选值归一化为数据集命名参数。
 * 作用域仅两层：系统（后端注入的保留名）与面板私有（裸参数名，仅注入本面板取数请求）。
 *
 * @author huxuehao
 */
import dayjs, { type Dayjs } from 'dayjs'
import type { DashboardFilter } from '@/types/dashboard'

/** 筛选值集合，按 filter.id 存储控件原始值 */
export type FilterValues = Record<string, unknown>

/** 系统保留参数名（后端注入，前端不得占用） */
export const RESERVED_PARAM_KEYS = ['currentTenantId', 'currentUserId']

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
 * - date / month / year → <paramKey>（YYYY-MM-DD / YYYY-MM / YYYY）
 * - select / text → <paramKey>
 * 未选择时也注入 null：保证 SQL 引用参数不报错，并支持 (:p is null or ...) 可选过滤。
 */
export function buildFilterParams(
  filters: DashboardFilter[] | undefined,
  values: FilterValues,
): Record<string, unknown> {
  const params: Record<string, unknown> = {}
  ;(filters || []).forEach((f) => {
    const v = values[f.id]
    if (f.type === 'dateRange') {
      const valid = Array.isArray(v) && v.length === 2 && v[0] && v[1]
      params[`${f.paramKey}Start`] = valid ? formatDate(v[0] as Dayjs | string, 'YYYY-MM-DD') : null
      params[`${f.paramKey}End`] = valid ? formatDate(v[1] as Dayjs | string, 'YYYY-MM-DD') : null
    } else if (f.type === 'date' || f.type === 'month' || f.type === 'year') {
      const fmt = f.type === 'date' ? 'YYYY-MM-DD' : f.type === 'month' ? 'YYYY-MM' : 'YYYY'
      params[f.paramKey] = v ? formatDate(v as Dayjs | string, fmt) : null
    } else {
      params[f.paramKey] = v !== undefined && v !== null && v !== '' ? v : null
    }
  })
  return params
}

function formatDate(v: Dayjs | string, fmt: string): string {
  return dayjs(v).format(fmt)
}

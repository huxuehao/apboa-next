/**
 * Dashboard 相关类型定义
 *
 * @author huxuehao
 */
import type { Component } from 'vue'

/** 栅格配置 */
export interface GridConfig {
  cols: number
  rowHeight: number
  margin: [number, number]
  responsive?: boolean
}

/** 刷新配置 */
export interface RefreshConfig {
  enabled: boolean
  interval: number
}

/** 面板在栅格中的位置与尺寸 */
export interface PanelLayout {
  x: number
  y: number
  w: number
  h: number
}

/** 数据集引用 */
export interface DatasetRef {
  id: string
  params?: Record<string, unknown>
}

/** 面板 DSL */
export interface PanelDsl {
  id: string
  type: string
  title?: string
  /** 是否显示标题栏，默认 true */
  showTitle?: boolean
  layout: PanelLayout
  dataset?: DatasetRef | null
  fieldMapping?: Record<string, unknown>
  options?: Record<string, unknown>
  style?: Record<string, string>
  refresh?: RefreshConfig
}

/** Dashboard DSL */
export interface DashboardDsl {
  version: number
  grid: GridConfig
  refresh?: RefreshConfig
  /** 全局筛选器（改变后联动刷新所有面板） */
  filters?: DashboardFilter[]
  panels: PanelDsl[]
}

/** 全局筛选器类型 */
export type DashboardFilterType = 'dateRange' | 'select' | 'text'

/** 全局筛选器定义 */
export interface DashboardFilter {
  id: string
  type: DashboardFilterType
  label: string
  /** 生成的命名参数基名；dateRange 会派生出 <paramKey>Start / <paramKey>End */
  paramKey: string
  /** select 类型的候选项 */
  options?: { label: string; value: string }[]
  /** 默认值 */
  default?: unknown
}

/** 结果列信息 */
export interface ColumnMeta {
  name: string
  type?: string
}

/** 数据集执行结果 */
export interface DatasetExecuteResult {
  columns: ColumnMeta[]
  rows: Record<string, unknown>[]
  rowCount: number
  elapsedMs: number
  truncated: boolean
}

/** Dashboard 模板实体 */
export interface DashboardEntity {
  id?: string
  name?: string
  remark?: string
  status?: string
  isDefault?: boolean
  version?: string
  config?: DashboardDsl
  enabled?: boolean
}

/** 数据集实体 */
export interface DashboardDatasetEntity {
  id?: string
  name?: string
  remark?: string
  sqlText?: string
  params?: unknown
  resultSchema?: unknown
  cacheTtl?: number
  datasourceId?: string
  enabled?: boolean
}

/** 个人副本实体 */
export interface DashboardUserEntity {
  id?: string
  dashboardId?: string
  config?: DashboardDsl
  basedVersion?: string
}

/** 门户解析结果 */
export interface PortalDashboard {
  dashboardId: string
  source: 'TEMPLATE' | 'PERSONAL'
  templateVersion?: string
  basedVersion?: string
  stale: boolean
  config: DashboardDsl
}

/** 面板配置项类型（声明式配置表单） */
export type PanelConfigFieldType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'switch'
  | 'select'
  | 'color'
  | 'icon'
  | 'field'
  | 'fields'

/** 面板配置项声明 */
export interface PanelConfigField {
  key: string
  label: string
  type: PanelConfigFieldType
  options?: { label: string; value: unknown }[]
  placeholder?: string
  group?: string
  default?: unknown
}

/** 面板数据需求 */
export interface PanelDataRequirement {
  needsDataset: boolean
  /** 是否支持绑定数据集（默认 true）；快捷方式等纯展示面板设为 false 以隐藏数据集配置 */
  supportsDataset?: boolean
}

/** 面板描述符（注册表契约） */
export interface PanelDefinition {
  type: string
  name: string
  category: string
  icon?: Component
  component: Component
  defaultDsl: () => Partial<PanelDsl>
  configSchema: PanelConfigField[]
  dataRequirement: PanelDataRequirement
}

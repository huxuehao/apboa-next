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
  /** 标题图标（Outlined 图标名，样式跟随标题文本） */
  titleIcon?: string
  /** 是否显示标题文本，默认 true */
  showTitle?: boolean
  /** 是否显示整个标题栏，默认 true */
  showHeader?: boolean
  /** 面板私有筛选器配置（仅 supportsPanelFilters 的面板生效） */
  panelFilter?: PanelFilterConfig
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
  panels: PanelDsl[]
}

/** 筛选器类型 */
export type DashboardFilterType = 'dateRange' | 'date' | 'month' | 'year' | 'select' | 'text'

/** 筛选器定义（面板私有） */
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

/** 面板私有筛选器配置 */
export interface PanelFilterConfig {
  /** 是否启用（可关闭） */
  enabled: boolean
  /** 位置：标题栏右侧 / 内容区四角（左上、右上、左下、右下） */
  position: 'header' | 'contentTopLeft' | 'contentTopRight' | 'contentBottomLeft' | 'contentBottomRight'
  /** 控件尺寸 */
  size: 'large' | 'middle' | 'small'
  /** 是否显示筛选项名称（整栏统一） */
  showLabel: boolean
  /** 筛选项（以裸参数名注入本面板取数请求） */
  items: DashboardFilter[]
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

/** 数据集类型 */
export type DatasetType = 'SQL' | 'HTTP'

/** HTTP 查询参数：value 支持 :name 模板（绑定筛选/系统参数），default 为回退默认值 */
export interface HttpQueryParam {
  key: string
  value: string
  default?: string
}

/** HTTP 固定请求头 */
export interface HttpHeaderItem {
  key: string
  value: string
}

/** HTTP 数据集配置 */
export interface HttpDatasetConfig {
  url: string
  queries: HttpQueryParam[]
  headers: HttpHeaderItem[]
  dataPath?: string
}

/** 数据集实体 */
export interface DashboardDatasetEntity {
  id?: string
  name?: string
  remark?: string
  type?: DatasetType
  sqlText?: string
  params?: unknown
  resultSchema?: unknown
  cacheTtl?: number
  datasourceId?: string
  httpConfig?: HttpDatasetConfig
  enabled?: boolean
}

/** 个人副本实体 */
export interface DashboardUserEntity {
  id?: string
  dashboardId?: string
  config?: DashboardDsl
  basedVersion?: string
}

/** 个人历史版本 */
export interface DashboardHistoryEntity {
  id: string
  dashboardId: string
  config: DashboardDsl
  note?: string
  createdAt?: string
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
  | 'portalComponent'
  | 'propsList'
  | 'columnPicker'

/** 面板标题栏操作按钮（由面板组件运行时注册，通用机制） */
export interface PanelAction {
  key: string
  label: string
  /** iconRegistry 中的图标名（可选） */
  icon?: string
  run: () => void
}

/** portal 自定义组件元信息（defineOptions({ portalMeta }) 约定） */
export interface PortalMeta {
  name?: string
  description?: string
}

/** 自定义组件 props 条目类型 */
export type PanelPropType = 'string' | 'number' | 'boolean' | 'array' | 'date' | 'object'

/** 自定义组件 props 条目（array/object 的 value 以 JSON 字符串存储，date 为 YYYY-MM-DD） */
export interface PanelPropItem {
  key: string
  type: PanelPropType
  value: unknown
}

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
  /** 是否支持面板私有筛选器（默认 false，注册表驱动） */
  supportsPanelFilters?: boolean
}

/** 样式覆盖分组：card/header 由 PanelRenderer 统一渲染全员支持，text 由文字类面板显式声明 */
export type PanelStyleGroup = 'card' | 'header' | 'text'

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
  /** 样式覆盖分组：缺省 ['card','header']；文字类面板需额外声明 'text' */
  styleGroups?: PanelStyleGroup[]
}

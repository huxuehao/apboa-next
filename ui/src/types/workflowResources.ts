import type { PageParams } from './common'
import type { CacheType, DatasourceType, HealthStatus, MqType } from './enums'

export type WorkflowResourceKind = 'datasource' | 'cache' | 'mq'
export type DatasourceTypeValue = `${DatasourceType}`
export type CacheTypeValue = `${CacheType}`
export type MqTypeValue = `${MqType}`

export interface WorkflowResourceSummary {
  total: number
  datasourceTotal: number
  cacheTotal: number
  mqTotal: number
  datasourceEnabled: number
  cacheEnabled: number
  mqEnabled: number
}

export interface WorkflowResourceBase {
  id?: string
  tenantId?: string
  name?: string
  remark?: string
  type?: string
  config?: string
  username?: string
  password?: string
  enabled?: boolean
  healthStatus?: HealthStatus
  lastHealthCheck?: string
  lastCheckMessage?: string
  createdAt?: string
  updatedAt?: string
}

export interface DatasourceResource extends WorkflowResourceBase {
  type?: DatasourceTypeValue
  ip?: string
  port?: string
  db?: string
}

export interface CacheResource extends WorkflowResourceBase {
  type?: CacheTypeValue
  ip?: string
  port?: number
  db?: number
}

export interface MqResource extends WorkflowResourceBase {
  type?: MqTypeValue
  address?: string
  port?: number
}

export type WorkflowManagedResource = DatasourceResource | CacheResource | MqResource

export interface WorkflowResourceQuery extends PageParams {
  name?: string
  type?: string
  enabled?: boolean
  healthStatus?: HealthStatus
}

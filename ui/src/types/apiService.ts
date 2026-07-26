/**
 * API服务（网关）相关类型定义
 *
 * @author huxuehao
 */

/** 参数位置 */
export type GatewayParamPosition = 'PATH' | 'QUERY' | 'HEADER' | 'BODY'

/** 参数类型 */
export type GatewayParamType = 'STRING' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'BOOLEAN' | 'OBJECT' | 'ARRAY'

/** 鉴权类型 */
export type GatewayAuthType = 'TOKEN' | 'NONE'

/** 限流类型 */
export type GatewayLimitType = 'NONE' | 'MINUTE' | 'HOUR' | 'DAY'

/** 请求方法 */
export type GatewayHttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'ALL'

/** API参数定义 */
export interface GatewayApiParam {
  position: GatewayParamPosition
  key: string
  type: GatewayParamType
  required: boolean
  defaultVal?: string
  workflowParam?: string
  remark?: string
}

/** API配置 */
export interface GatewayApiConfig {
  authType: GatewayAuthType
  authHeaderName: string
  limitType: GatewayLimitType
  routeTimes?: number
  ipTimes?: number
  contentTypes?: string[]
  params: GatewayApiParam[]
  wholeBodyParam?: string
}

/** 应用配置 */
export interface GatewayAppConfig {
  corsOpen?: boolean
  allowedOrigin?: string
  allowCredentials?: boolean
  maxAgeSeconds?: number
  allowedMethods?: string[]
  contentLength?: number
}

/** 网关应用 */
export interface GatewayApp {
  id?: string
  name: string
  remark?: string
  protocol?: string
  port?: number
  config?: GatewayAppConfig
  online?: number
  createdAt?: string
}

/** 网关API */
export interface GatewayApi {
  id?: string
  appId?: string
  category?: string
  name: string
  remark?: string
  method: GatewayHttpMethod
  path: string
  config?: GatewayApiConfig
  online?: number
  createdAt?: string
  // 关联信息
  appName?: string
  appPort?: number
  workflowId?: string
  workflowName?: string
  workflowStatus?: string
}

/** 网关客户端 */
export interface GatewayClient {
  id?: string
  code: string
  name: string
  consumer?: string
  expireAt?: string | null
  tokenSecret?: string
  tokenTtl?: number
  online?: number
  createdAt?: string
  apiIds?: string[]
  apiCount?: number
}

/** 访问日志 */
export interface GatewayAccessLog {
  id: string
  appId?: string
  apiId?: string
  workflowRunId?: string
  method?: string
  path?: string
  headerParams?: string
  pathParams?: string
  queryParams?: string
  requestBody?: string
  responseBody?: string
  accessIp?: string
  status?: number
  httpStatus?: number
  error?: string
  startTime?: number
  endTime?: number
  createdAt?: string
}

/** Token颁发日志 */
export interface GatewayTokenLog {
  id: string
  clientCode?: string
  accessIp?: string
  status?: number
  error?: string
  createdAt?: string
}

/** 分页结果 */
export interface GatewayPageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

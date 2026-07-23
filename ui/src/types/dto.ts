/**
 * DTO类型定义
 *
 * @author huxuehao
 */

import type { PageParams } from './common'
import type { HookType, ToolType, ModelType, KbType, McpProtocol, TenantRole, TenantJoinRequestStatus } from './enums'

/**
 * 账号查询DTO
 */
export interface AccountDTO extends PageParams {
  nickname?: string
  email?: string
  username?: string
  enabled?: boolean
}

/**
 * 智能体定义查询DTO
 */
export interface AgentDefinitionDTO extends PageParams {
  name?: string
  agentType?: 'CUSTOM' | 'A2A'
  agentCode?: string
  enabled?: boolean
  tag?: string
}

/**
 * Hook配置查询DTO
 */
export interface HookConfigDTO extends PageParams {
  name?: string
  hookType?: HookType
  enabled?: boolean
}

/**
 * 知识库配置查询DTO
 */
export interface KnowledgeBaseConfigDTO extends PageParams {
  name?: string
  kbType?: KbType
  enabled?: boolean
}

/**
 * MCP服务器查询DTO
 */
export interface McpServerDTO extends PageParams {
  name?: string
  protocol?: McpProtocol
  enabled?: boolean
}

/**
 * 模型配置查询DTO
 */
export interface ModelConfigDTO extends PageParams {
  providerId?: string
  name?: string
  enabled?: boolean
}

/**
 * 模型提供商查询DTO
 */
export interface ModelProviderDTO extends PageParams {
  name?: string
  type?: string
  enabled?: boolean
}

/**
 * 敏感词配置查询DTO
 */
export interface SensitiveWordConfigDTO extends PageParams {
  category?: string
  name?: string
  enabled?: boolean
}

/**
 * 技能包查询DTO
 */
export interface SkillPackageDTO extends PageParams {
  name?: string
  category?: string
  skillType?: string
  enabled?: boolean
}

/**
 * 本地导入技能包配置
 */
export interface LocalImportConfig {
  /** 技能分类 */
  category: string
  /** 本地路径 */
  path: string
  /** 是否覆盖已存在的同名技能 */
  cover: boolean
}

/**
 * Git导入技能包配置
 */
export interface GitImportConfig {
  /** 技能分类 */
  category: string
  /** 仓库地址 */
  repoUrl: string
  /** 是否覆盖已存在的同名技能 */
  cover: boolean
}

/**
 * 系统提示词模板查询DTO
 */
export interface SystemPromptTemplateDTO extends PageParams {
  category?: string
  name?: string
  enabled?: boolean
}

/**
 * 工具查询DTO
 */
export interface ToolDTO extends PageParams {
  name?: string
  toolId?: string
  toolType?: ToolType
  category?: string
  enabled?: boolean
}

/**
 * 修改密码请求
 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/**
 * 登录请求
 */
export interface LoginRequest {
  username: string
  password: string
  /** 租户ID（多租户用户选择租户登录时携带） */
  tenantId?: string
}

/**
 * 用户详情（登录返回）
 */
export interface UserDetail {
  id: string
  name: string
  username: string
  email: string
  /** 当前租户ID */
  tenantId?: string | null
  /** 当前租户编码 */
  tenantCode?: string | null
  /** 当前租户内角色 */
  tenantRole?: string | null
  /** 当前租户名称 */
  tenantName?: string | null
  /** 可切换的租户列表 */
  tenants?: TenantInfo[]
}

/**
 * 租户简要信息
 */
export interface TenantInfo {
  tenantId: string
  tenantCode: string
  tenantName: string
  role: string
}

/**
 * 登录响应
 */
export interface LoginResponse {
  accessToken: string
  accessTokenTTL: string
  refreshToken: string
  refreshTokenTTL: string
  userDetail: UserDetail
  /** 是否需要选择租户 */
  needSelectTenant?: boolean
  /** 可选择的租户列表 */
  tenants?: TenantInfo[]
  /** 登录是否被阻断（有待审批申请时返回 true） */
  blocked?: boolean
  /** 待审批的加入申请列表 */
  pendingApprovals?: PendingApprovalInfo[]
}

/**
 * 待审批加入申请信息（登录被阻时返回）
 */
export interface PendingApprovalInfo {
  requestId: number
  tenantId: number
  tenantName: string
  tenantCode: string
  status: string
  createdAt: string
}

/**
 * 刷新Token请求
 */
export interface RefreshTokenRequest {
  refreshToken: string
}

/**
 * 注册请求
 */
export interface RegisterRequest {
  nickname: string
  username: string
  email: string
  password: string
  /** 是否同时创建租户 */
  createTenant?: boolean
  /** 租户名称 */
  tenantName?: string
  /** 租户编码 */
  tenantCode?: string
  /** 租户描述 */
  tenantDescription?: string
  /** 要加入的租户ID（与 createTenant 互斥） */
  joinTenantId?: string
  /** 加入申请附言（可选） */
  joinMessage?: string
}

/**
 * 更新用户信息请求
 */
export interface UpdateProfileRequest {
  nickname?: string
  email?: string
  rememberLastTenant?: boolean
}

/**
 * 创建会话DTO
 */
export interface ChatSessionCreateDTO {
  agentId: string
  title?: string
  initWorkspace?: boolean
}

/**
 * 追加消息DTO（正常对话或重新生成）
 */
export interface ChatMessageAppendDTO {
  role: string
  content: string
}

/**
 * 租户查询DTO
 */
export interface TenantDTO extends PageParams {
  name?: string
  code?: string
  status?: number
  enabled?: boolean
}

/**
 * 选择租户请求
 */
export interface SelectTenantRequest {
  tenantId: string
}

/**
 * 租户创建/更新请求
 */
export interface TenantCreateRequest {
  name: string
  code: string
  description?: string
  contactName?: string
  contactEmail?: string
}

/**
 * 租户治理设置请求
 */
export interface TenantSettingsRequest {
  discoverable?: boolean
  joinable?: boolean
  joinApprovalRequired?: boolean
  name?: string
  description?: string
  contactName?: string
  contactEmail?: string
}

/**
 * 租户加入申请请求
 */
export interface TenantJoinRequestDTO {
  message: string
}

/**
 * 租户发现VO（含成员身份信息）
 */
export interface TenantDiscoveryVO {
  id: string
  name: string
  code: string
  description: string
  contactName: string
  contactEmail: string
  discoverable: boolean
  joinable: boolean
  joinApprovalRequired: boolean
  /** 当前用户是否已加入 */
  isJoined: boolean
  /** 当前用户在该租户中的角色（已加入时有值） */
  role: string | null
  /** 是否为当前登录的租户 */
  isCurrent: boolean
}

/**
 * 租户成员添加请求
 */
export interface TenantMemberAddDTO {
  username: string
  role: TenantRole
}

/**
 * 会话列表查询DTO
 */
export interface ChatSessionQueryDTO {
  userId?: string
  agentId?: string
  isPinned?: boolean
  page?: number
  size?: number
}

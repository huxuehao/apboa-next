/**
 * 枚举类型定义
 *
 * @author huxuehao
 */

/**
 * 角色（已废弃，由 TenantRole 替代）
 * @deprecated
 */
export enum Role {
  READ_ONLY = 'READ_ONLY',
  EDIT = 'EDIT',
  ADMIN = 'ADMIN'
}

/**
 * 工具选择策略
 */
export enum ToolChoiceStrategy {
  AUTO = 'AUTO',
  NONE = 'NONE',
  REQUIRED = 'REQUIRED',
  SPECIFIC = 'SPECIFIC'
}

/**
 * Hook类型
 */
export enum HookType {
  BUILTIN = 'BUILTIN',
  CUSTOM = 'CUSTOM'
}

/**
 * 工具类型
 */
export enum ToolType {
  BUILTIN = 'BUILTIN',
  CUSTOM = 'CUSTOM'
}

/**
 * 代码语言
 */
export enum CodeLanguage {
  JAVA = 'JAVA',
  JAVASCRIPT = 'JAVASCRIPT'
}

/**
 * 认证类型
 */
export enum AuthType {
  CONFIG = 'CONFIG',
  ENV = 'ENV'
}

/**
 * 模型提供商类型
 */
export enum ModelProviderType {
  DASH_SCOPE = 'DASH_SCOPE',
  OPEN_AI = 'OPEN_AI',
  ANTHROPIC = 'ANTHROPIC',
  GEMINI = 'GEMINI',
  OLLAMA = 'OLLAMA'
}

/**
 * 模型类型
 */
export enum ModelType {
  CHAT = 'CHAT',
  IMAGE = 'IMAGE',
  VIDEO = 'VIDEO',
  AUDIO = 'AUDIO'
}

/**
 * 健康状态
 */
export enum HealthStatus {
  HEALTHY = 'HEALTHY',
  UNHEALTHY = 'UNHEALTHY',
  UNKNOWN = 'UNKNOWN'
}

/**
 * 工作流数据源类型
 */
export enum DatasourceType {
  MYSQL = 'MYSQL',
  ORACLE = 'ORACLE',
  POSTGRESQL = 'POSTGRESQL'
}

/**
 * 工作流缓存类型
 */
export enum CacheType {
  REDIS = 'REDIS'
}

/**
 * 工作流消息队列类型
 */
export enum MqType {
  KAFKA = 'KAFKA',
  RABBITMQ = 'RABBITMQ',
  ROCKETMQ = 'ROCKETMQ'
}

/**
 * 知识库类型
 */
export enum KbType {
  BAILIAN = 'BAILIAN',
  DIFY = 'DIFY',
  RAGFLOW = 'RAGFLOW',
  LOCAL = 'LOCAL'
}

/**
 * MCP运行模式
 */
export enum McpMode {
  SYNC = 'SYNC',
  ASYNC = 'ASYNC'
}

/**
 * MCP协议
 */
export enum McpProtocol {
  HTTP = 'HTTP',
  SSE = 'SSE',
  STDIO = 'STDIO'
}

/**
 * MCP 激活状态
 */
export enum McpActivationStatus {
  NOT_ACTIVATED = 'NOT_ACTIVATED',
  ACTIVATING = 'ACTIVATING',
  ACTIVE = 'ACTIVE',
  FAILED = 'FAILED'
}

/**
 * MCP 失败来源
 */
export enum McpFailureSource {
  NONE = 'NONE',
  RUNTIME_AUTO_DEGRADE = 'RUNTIME_AUTO_DEGRADE'
}

/**
 * MCP 工具暴露模式
 */
export enum McpToolExposureMode {
  ALL_GLOBAL = 'ALL_GLOBAL',
  SELECTED_ONLY = 'SELECTED_ONLY'
}

/**
 * 敏感词处理动作
 */
export enum SensitiveWordAction {
  BLOCK = 'BLOCK',
  REPLACE = 'REPLACE',
  WARN = 'WARN'
}

/**
 * RAG模式
 */
export enum RAGMode {
  GENERIC = 'GENERIC',
  AGENTIC = 'AGENTIC'
}

/**
 * A2A协议类型
 */
export enum A2aType {
  WELLKNOWN = 'WELLKNOWN',
  NACOS = 'NACOS'
}

/**
 * 模型连接性检测状态
 */
export enum ModelConnectivityStatus {
  NOT_CHECKED = 'NOT_CHECKED',
  CHECKING = 'CHECKING',
  CONNECTED = 'CONNECTED',
  FAILED = 'FAILED'
}

/**
 * 租户内角色
 */
export enum TenantRole {
  TENANT_OWNER = 'TENANT_OWNER',
  TENANT_ADMIN = 'TENANT_ADMIN',
  TENANT_EDITOR = 'TENANT_EDITOR',
  TENANT_VIEWER = 'TENANT_VIEWER'
}

/**
 * 租户加入申请状态
 */
export enum TenantJoinRequestStatus {
  PENDING = 'PENDING',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  CANCELLED = 'CANCELLED'
}

/**
 * 长期记忆类型
 */
export enum LongTermMemoryType {
  MEM0 = 'MEM0',
  REME = 'REME',
  BAILIAN = 'BAILIAN'
}

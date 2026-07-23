/**
 * 路由常量
 *
 * @author huxuehao
 */

/**
 * 路由名称常量
 */
export const RouteNames = {
  // 认证相关
  LOGIN: 'Login',
  REGISTER: 'Register',
  FORGOT_PASSWORD: 'ForgotPassword',
  PREVIEW: 'Preview',

  // 布局
  LAYOUT: 'Layout',

  // 主页
  HOME: 'Home',
  DASHBOARD: 'Dashboard',
  AUTOMATION: 'Automation',
  CHAT_CLUSTER: 'ChatCluster',

  // Agent管理
  AGENT: 'Agent',
  WORKFLOW: 'Workflow',
  WORKFLOW_NEW: 'WorkflowNew',
  WORKFLOW_EDIT: 'WorkflowEdit',

  // 模型管理
  MODEL: 'Model',
  MODEL_PROVIDER_CONFIG: 'ModelProviderConfig',

  // 成本中心
  COST: 'Cost',

  // 提示词管理
  PROMPT: 'Prompt',

  // 知识库管理
  KNOWLEDGE: 'Knowledge',

  // 工具管理
  TOOL: 'Tool',

  // Hook管理
  HOOK: 'Hook',

  // 技能包管理
  SKILL: 'Skill',
  SKILL_EDITOR: 'SkillEditor',
  SKILL_EDITOR_NEW: 'SkillEditorNew',

  // MCP管理
  MCP: 'Mcp',
  MCP_TOOL_GOVERNANCE: 'McpToolGovernance',

  // 敏感词管理
  SENSITIVE: 'Sensitive',

  // 账号管理
  ACCOUNT: 'Account',

  // 系统管理
  SYSTEM: 'System',

  // 智能体对话
  CHAT: 'Chat',
  CHAT_HISTORY: 'ChatHistory',
  COMMUNICATION: 'Communication',

  // 设置管理
  SETTINGS_ACCOUNT: 'SettingsAccount',
  SETTINGS_TENANT: 'SettingsTenant',
  SETTINGS_TENANT_DISCOVERY: 'SettingsTenantDiscovery',
  SETTINGS_SYSTEM_PARAMS: 'SettingsSystemParams',
  SETTINGS_SYSTEM_INTRO: 'SettingsSystemIntro',
  SETTINGS_API_KEYS: 'SettingsApiKeys',

  // 运维管理
  OPS_MONITOR: 'OpsMonitor',
  OPS_STORAGE: 'OpsStorage',

  // 审查管理
  REVIEW_AGENT: 'ReviewAgent',
  REVIEW_WORKFLOW: 'ReviewWorkflow',

  // 错误页面
  NOT_FOUND: 'NotFound'
} as const

/**
 * 路由路径常量
 */
export const RoutePaths = {
  // 认证相关
  LOGIN: '/login',
  REGISTER: '/register',
  FORGOT_PASSWORD: '/forgot-password',
  PREVIEW: '/preview',

  // 主页
  ROOT: '/',
  HOME: '/home',
  DASHBOARD: '/dashboard',
  AUTOMATION: '/automation',
  CHAT_CLUSTER: '/chat-cluster',

  // Agent管理
  AGENT: 'agent',
  WORKFLOW: 'workflow',
  WORKFLOW_NEW: '/workflow/new',
  WORKFLOW_EDIT: '/workflow/:id',

  // 模型管理
  MODEL: 'model',
  MODEL_PROVIDER_CONFIG: '/model/:providerId/config',

  // 成本中心
  COST: 'cost',

  // 提示词管理
  PROMPT: 'prompt',

  // 知识库管理
  KNOWLEDGE: 'knowledge',

  // 知识库文档管理
  KNOWLEDGE_DOCUMENTS: '/knowledge/:id/documents',

  // 工具管理
  TOOL: 'tool',

  // Hook管理
  HOOK: 'hook',

  // 技能包管理
  SKILL: 'skill',
  SKILL_NEW: 'skill/new',
  SKILL_EDIT: 'skill/:id/edit',

  // MCP管理
  MCP: 'mcp',
  MCP_TOOL_GOVERNANCE: '/mcp/:serverId/tools',

  // 敏感词管理
  SENSITIVE: 'sensitive',

  // 账号管理
  ACCOUNT: '/account',

  // 智能体对话
  CHAT: '/chat',
  CHAT_HISTORY: '/chat/history',
  COMMUNICATION: '/communication',

  // 设置管理
  SETTINGS_ACCOUNT: 'settings/account',
  SETTINGS_TENANT: 'settings/tenant',
  SETTINGS_TENANT_DISCOVERY: 'settings/tenant-discovery',
  SETTINGS_SYSTEM_PARAMS: 'settings/system-params',
  SETTINGS_SYSTEM_INTRO: 'settings/system-intro',
  SETTINGS_API_KEYS: 'settings/api-keys',

  // 运维管理
  OPS_MONITOR: 'ops/monitor',
  OPS_STORAGE: 'ops/storage',

  // 审查管理
  REVIEW_AGENT: 'review/agent',
  REVIEW_WORKFLOW: 'review/workflow',

  // 错误页面
  NOT_FOUND: '/:pathMatch(.*)*',
  FORBIDDEN: '/403',
  SERVER_ERROR: '/500',
} as const

/**
 * 白名单路由（无需认证）
 */
export const WHITE_LIST = [
  RoutePaths.LOGIN,
  RoutePaths.REGISTER,
  RoutePaths.FORGOT_PASSWORD,
  RoutePaths.PREVIEW,
]

/**
 * 重定向路由
 */
export const REDIRECT_ROUTE_NAME = 'Redirect'

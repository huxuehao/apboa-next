/**
 * 公共页面路由
 *
 * @author huxuehao
 */

import type { AppRouteRecordRaw } from '../types'
import { RouteNames, RoutePaths } from '../constants'

/**
 * 业务路由配置
 */
const bizRoutes: AppRouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: RoutePaths.SENSITIVE,
        name: RouteNames.SENSITIVE,
        component: () => import('@/views/Sensitive/index.vue'),
        meta: {
          title: '敏感词',
          hidden: false
        },
      },
      {
        path: RoutePaths.PROMPT,
        name: RouteNames.PROMPT,
        component: () => import('@/views/Prompt/index.vue'),
        meta: {
          title: '系统提示词模版',
          hidden: false
        },
      },
      {
        path: RoutePaths.MODEL,
        name: RouteNames.MODEL,
        component: () => import('@/views/Model/index.vue'),
        meta: {
          title: '模型供应商',
          hidden: false
        },
      },
      {
        path: RoutePaths.MODEL_PROVIDER_CONFIG,
        name: RouteNames.MODEL_PROVIDER_CONFIG,
        component: () => import('@/views/Model/ProviderConfig.vue'),
        meta: {
          title: '模型配置',
          hidden: true
        },
      },

      {
        path: RoutePaths.AGENT,
        name: RouteNames.AGENT,
        component: () => import('@/views/Agent/index.vue'),
        meta: {
          title: '智能体',
          hidden: false
        },
      },
      {
        path: RoutePaths.WORKFLOW,
        name: RouteNames.WORKFLOW,
        component: () => import('@/views/Workflow/index.vue'),
        meta: {
          title: '工作流',
          hidden: false
        },
      },
      {
        path: RoutePaths.HOOK,
        name: RouteNames.HOOK,
        component: () => import('@/views/Hook/index.vue'),
        meta: {
          title: '钩子',
          hidden: false
        },
      },
      {
        path: RoutePaths.TOOL,
        name: RouteNames.TOOL,
        component: () => import('@/views/Tool/index.vue'),
        meta: {
          title: '工具',
          hidden: false
        },
      },
      {
        path: RoutePaths.SKILL,
        name: RouteNames.SKILL,
        component: () => import('@/views/Skill/index.vue'),
        meta: {
          title: '技能包',
          hidden: false
        },
      },
      {
        path: RoutePaths.SKILL_NEW,
        name: RouteNames.SKILL_EDITOR_NEW,
        component: () => import('@/views/Skill/SkillEditorView.vue'),
        meta: {
          title: '新建技能包',
          hidden: true,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.SKILL_EDIT,
        name: RouteNames.SKILL_EDITOR,
        component: () => import('@/views/Skill/SkillEditorView.vue'),
        meta: {
          title: '编辑技能包',
          hidden: true,
          hideFooter: true,
        },
      },
      {
        path: RoutePaths.MCP,
        name: RouteNames.MCP,
        component: () => import('@/views/Mcp/index.vue'),
        meta: {
          title: 'MCP',
          hidden: false
        },
      },
      {
        path: RoutePaths.MCP_TOOL_GOVERNANCE,
        name: RouteNames.MCP_TOOL_GOVERNANCE,
        component: () => import('@/views/Mcp/ToolGovernance.vue'),
        meta: {
          title: '工具治理',
          hidden: true
        },
      },
      {
        path: RoutePaths.KNOWLEDGE,
        name: RouteNames.KNOWLEDGE,
        component: () => import('@/views/Knowledge/index.vue'),
        meta: {
          title: '知识库',
          hidden: false
        },
      },
      // 工作台
      {
        path: 'dashboard',
        name: RouteNames.DASHBOARD,
        component: () => import('@/views/Dashboard/index.vue'),
        meta: {
          title: '工作台',
          hidden: false
        },
      },
      // 设置管理
      {
        path: RoutePaths.SETTINGS_ACCOUNT,
        name: RouteNames.SETTINGS_ACCOUNT,
        component: () => import('@/views/Settings/Account.vue'),
        meta: {
          title: '我的账号',
          hidden: false
        },
      },
      {
        path: RoutePaths.SETTINGS_TENANT,
        name: RouteNames.SETTINGS_TENANT,
        component: () => import('@/views/Settings/Tenant.vue'),
        meta: {
          title: '组织管理',
          hidden: false
        },
      },
      {
        path: RoutePaths.SETTINGS_TENANT_DISCOVERY,
        name: RouteNames.SETTINGS_TENANT_DISCOVERY,
        component: () => import('@/views/Settings/TenantDiscovery.vue'),
        meta: {
          title: '发现组织',
          hidden: false
        },
      },
      {
        path: RoutePaths.SETTINGS_SYSTEM_PARAMS,
        name: RouteNames.SETTINGS_SYSTEM_PARAMS,
        component: () => import('@/views/Settings/SystemParams.vue'),
        meta: {
          title: '系统参数',
          hidden: false
        },
      },
      {
        path: RoutePaths.SETTINGS_SYSTEM_INTRO,
        name: RouteNames.SETTINGS_SYSTEM_INTRO,
        component: () => import('@/views/Settings/SystemIntro.vue'),
        meta: {
          title: '系统介绍',
          hidden: false
        },
      },
      {
        path: RoutePaths.SETTINGS_API_KEYS,
        name: RouteNames.SETTINGS_API_KEYS,
        component: () => import('@/views/Settings/ApiKeys.vue'),
        meta: {
          title: 'API Keys',
          hidden: false
        },
      },
      // 运维管理
      {
        path: RoutePaths.OPS_MONITOR,
        name: RouteNames.OPS_MONITOR,
        component: () => import('@/views/Ops/Monitor.vue'),
        meta: {
          title: '服务监控',
          hidden: false
        },
      },
      {
        path: RoutePaths.OPS_STORAGE,
        name: RouteNames.OPS_STORAGE,
        component: () => import('@/views/Ops/Storage.vue'),
        meta: {
          title: '存储管理',
          hidden: false
        },
      },
      // 审查管理
      {
        path: RoutePaths.REVIEW_AGENT,
        name: RouteNames.REVIEW_AGENT,
        component: () => import('@/views/Review/Agent.vue'),
        meta: {
          title: '审查智能体',
          hidden: false
        },
      },
      {
        path: RoutePaths.REVIEW_WORKFLOW,
        name: RouteNames.REVIEW_WORKFLOW,
        component: () => import('@/views/Review/Workflow.vue'),
        meta: {
          title: '审查工作流',
          hidden: false
        },
      }
    ],
  },
  {
    path: RoutePaths.WORKFLOW_NEW,
    name: RouteNames.WORKFLOW_NEW,
    component: () => import('@/views/Workflow/WorkflowEditorView.vue'),
    meta: {
      title: '新建工作流',
      hidden: true,
      hideFooter: true,
    },
  },
  {
    path: RoutePaths.WORKFLOW_EDIT,
    name: RouteNames.WORKFLOW_EDIT,
    component: () => import('@/views/Workflow/WorkflowEditorView.vue'),
    meta: {
      title: '编辑工作流',
      hidden: true,
      hideFooter: true,
    },
  }
]

export default bizRoutes

/**
 * 卡片操作菜单项 composable
 * 统一管理各卡片组件的操作菜单项及图标，便于后续统一修改
 *
 * @author huxuehao
 */
import { h } from 'vue'
import type { VNode } from 'vue'
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FileTextOutlined,
  HistoryOutlined,
  MessageOutlined,
  PlayCircleOutlined,
  SettingOutlined,
  StopOutlined,
  SyncOutlined,
  TagOutlined,
  ToolOutlined,
  TranslationOutlined,
} from '@ant-design/icons-vue'

/** 菜单项类型 */
export type CardMenuItem =
  | { key: string; label: string; icon?: () => VNode; danger?: boolean }
  | { type: 'divider' }

/**
 * 创建「查看」菜单项
 */
export function createViewItem(): CardMenuItem {
  return { key: 'view', label: '查看', icon: () => h(EyeOutlined) }
}

/**
 * 创建「查看内容」菜单项（进入只读编辑器查看技能包文件正文）
 */
export function createViewContentItem(): CardMenuItem {
  return { key: 'viewContent', label: '查看内容', icon: () => h(FileTextOutlined) }
}

/**
 * 创建「编辑」菜单项
 */
export function createEditItem(): CardMenuItem {
  return { key: 'edit', label: '编辑', icon: () => h(EditOutlined) }
}

/**
 * 创建「启用/禁用」菜单项
 *
 * @param enabled 当前是否启用
 */
export function createEnableItem(enabled: boolean): CardMenuItem {
  return {
    key: 'enable',
    label: enabled ? '禁用' : '启用',
    icon: () => h(enabled ? StopOutlined : CheckCircleOutlined)
  }
}

/**
 * 创建「删除」菜单项
 */
export function createDeleteItem(): CardMenuItem {
  return { key: 'delete', label: '删除', danger: true, icon: () => h(DeleteOutlined) }
}

/**
 * 创建「去对话」菜单项
 */
export function createGoVisitItem(): CardMenuItem {
  return { key: 'goVisit', label: '对话', icon: () => h(MessageOutlined) }
}

/**
 * 创建「访问历史」菜单项
 */
export function createAccessLogItem(): CardMenuItem {
  return { key: 'accessLog', label: '历史对话', icon: () => h(HistoryOutlined) }
}

/**
 * 创建「配置模型」菜单项
 */
export function createConfigItem(): CardMenuItem {
  return { key: 'config', label: '模型', icon: () => h(SettingOutlined) }
}

/**
 * 创建「协议配置」菜单项
 */
export function createProtocolConfigItem(): CardMenuItem {
  return { key: 'protocolConfig', label: '协议配置', icon: () => h(SettingOutlined) }
}

/**
 * 创建「架构图」菜单项
 */
export function createArchitectureItem(): CardMenuItem {
  return { key: 'architecture', label: '架构图', icon: () => h(ApartmentOutlined) }
}

/**
 * 创建「定时」菜单项
 */
export function createTimingItem(): CardMenuItem {
  return { key: 'timing', label: '定时', icon: () => h(ClockCircleOutlined) }
}

/**
 * 创建「连接」菜单项
 */
export function createActivateItem(label = '连接'): CardMenuItem {
  return { key: 'activate', label, icon: () => h(PlayCircleOutlined) }
}

/**
 * 创建「刷新工具」菜单项
 */
export function createSyncItem(label = '刷新工具'): CardMenuItem {
  return { key: 'sync', label, icon: () => h(SyncOutlined) }
}

/**
 * 创建“工具治理”菜单项
 */
export function createToolGovernanceItem(): CardMenuItem {
  return { key: 'toolGovernance', label: '工具治理', icon: () => h(SettingOutlined) }
}

/**
 * 创建「配置」菜单项
 */
export function createConfigPanelItem(): CardMenuItem {
  return { key: 'configPanel', label: '设计', icon: () => h(SettingOutlined) }
}

/**
 * 创建「设置标签」菜单项
 */
export function createSetCategoryItem(): CardMenuItem {
  return { key: 'setCategory', label: '标签', icon: () => h(TagOutlined) }
}

/**
 * 创建「别名」菜单项（设置展示别名，仅展示层友好，不影响 name）
 */
export function createAliasItem(): CardMenuItem {
  return { key: 'alias', label: '别名', icon: () => h(TranslationOutlined) }
}

/**
 * 创建「改名」菜单项（修改展示名称，内置资源开放改名，不影响实际生效）
 */
export function createRenameItem(): CardMenuItem {
  return { key: 'rename', label: '改名', icon: () => h(EditOutlined) }
}

/**
 * 创建「关联工具」菜单项
 */
export function createToolLinkItem(): CardMenuItem {
  return { key: 'toolLink', label: '关联工具', icon: () => h(ToolOutlined) }
}

/**
 * 创建分隔线
 */
export function createDivider(): CardMenuItem {
  return { type: 'divider' }
}

/**
 * 智能体常用问题图标映射表与色板
 *
 * 静态具名导入精选图标，供配置表单（图标选择器）与对话欢迎页（卡片渲染）共用。
 * 不要改为 import * 全量导入，否则整个图标库会被打进 bundle。
 *
 * @author huxuehao
 */
import type { Component } from 'vue'
import {
  ShoppingCartOutlined,
  ScheduleOutlined,
  DeploymentUnitOutlined,
  InboxOutlined,
  ThunderboltOutlined,
  SafetyCertificateOutlined,
  HeartOutlined,
  FundOutlined,
  RobotOutlined,
  SearchOutlined,
  BulbOutlined,
  ToolOutlined,
  FileTextOutlined,
  BarChartOutlined,
  PieChartOutlined,
  LineChartOutlined,
  DatabaseOutlined,
  TeamOutlined,
  SettingOutlined,
  QuestionCircleOutlined,
  MessageOutlined,
  MailOutlined,
  GlobalOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  CarOutlined,
  ShopOutlined,
  ApiOutlined,
  CodeOutlined,
  BookOutlined,
  RocketOutlined
} from '@ant-design/icons-vue'

/**
 * 常用问题可选图标映射表（图标名 -> 组件）
 */
export const COMMON_QUESTION_ICONS: Record<string, Component> = {
  ShoppingCartOutlined,
  ScheduleOutlined,
  DeploymentUnitOutlined,
  InboxOutlined,
  ThunderboltOutlined,
  SafetyCertificateOutlined,
  HeartOutlined,
  FundOutlined,
  RobotOutlined,
  SearchOutlined,
  BulbOutlined,
  ToolOutlined,
  FileTextOutlined,
  BarChartOutlined,
  PieChartOutlined,
  LineChartOutlined,
  DatabaseOutlined,
  TeamOutlined,
  SettingOutlined,
  QuestionCircleOutlined,
  MessageOutlined,
  MailOutlined,
  GlobalOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  CarOutlined,
  ShopOutlined,
  ApiOutlined,
  CodeOutlined,
  BookOutlined,
  RocketOutlined
}

/**
 * 图标名列表（选择器网格按此顺序展示）
 */
export const COMMON_QUESTION_ICON_NAMES = Object.keys(COMMON_QUESTION_ICONS)

/**
 * 常用问题图标色板（antd 主题色系）
 */
export const COMMON_QUESTION_COLORS = [
  '#1677ff',
  '#52c41a',
  '#fa8c16',
  '#722ed1',
  '#eb2f96',
  '#13c2c2',
  '#f5222d',
  '#faad14'
]

/**
 * 单个智能体最多可配置的常用问题数量
 */
export const MAX_COMMON_QUESTIONS = 8

/**
 * 按名称解析图标组件，名称为空或不在映射表中时返回 null（调用方兜底不渲染图标）
 */
export function resolveCommonQuestionIcon(name?: string): Component | null {
  if (!name) return null
  return COMMON_QUESTION_ICONS[name] ?? null
}

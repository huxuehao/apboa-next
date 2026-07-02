import type { WorkflowResource } from '@/types/workflow'

/** 根据资源 ID 查找资源名称 */
export function resourceName(list: WorkflowResource[], id: unknown): string {
  return list.find((item) => item.id === id)?.name || String(id || '未选择')
}

/** 检查值是否为空（undefined / null / ''） */
export function isEmpty(value: unknown): boolean {
  return value === undefined || value === null || value === ''
}

/** 可读的模式映射 */
export const splitModeLabels: Record<string, string> = {
  SIMPLE: '简单分隔符',
  REGEX: '正则',
  FIXED_LENGTH: '固定长度',
  LINE_BREAK: '换行',
  KEY_VALUE: '键值对',
  MULTIPLE_DELIMITERS: '多分隔符',
}

export const matchTypeLabels: Record<string, string> = {
  EQUALS: '等于',
  CONTAINS: '包含',
}

export const strategyLabels: Record<string, string> = {
  FIRST: '第一个',
  LAST: '最后一个',
}

export const aggStrategyLabels: Record<string, string> = {
  ARRAY: '数组',
  MAP: 'Map',
  STRING: '字符串',
}

export const directionLabels: Record<string, string> = {
  ASC: '升序',
  DESC: '降序',
}

export const modeLabels: Record<string, string> = {
  COMPACT: '紧凑',
  PRETTY: '美化',
}

export const filterModeLabels: Record<string, string> = {
  SIMPLE: '简单',
  EXPRESSION: '表达式',
}

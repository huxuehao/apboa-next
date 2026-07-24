import { ref } from 'vue'
import type { ToolDebugHistoryItem, ToolDebugResultVO } from '@/types'

/**
 * 工具调试历史管理（独立于 MCP 调试历史，存储 key 互不影响）
 * 使用 localStorage 持久化，按时间倒序
 * 双重保护：条数上限 + 存储体积上限，防止大结果撑爆 localStorage
 *
 * @author vaulka
 */
const STORAGE_KEY = 'tool:debug:history'
/** 最多保留条数 */
const MAX_HISTORY = 20
/** 单条结果内容存储上限（字符数），超出则截断 */
const MAX_CONTENT_LENGTH = 8000
/** 整体序列化体积上限（字节），约 1.5MB */
const MAX_STORAGE_BYTES = 1.5 * 1024 * 1024

function loadFromStorage(): ToolDebugHistoryItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveToStorage(items: ToolDebugHistoryItem[]): void {
  try {
    let serialized = JSON.stringify(items)
    // 体积超限时，从最旧的记录开始淘汰，直到满足限制
    while (serialized.length > MAX_STORAGE_BYTES && items.length > 1) {
      items.pop()
      serialized = JSON.stringify(items)
    }
    localStorage.setItem(STORAGE_KEY, serialized)
  } catch {
    // localStorage 写入失败时（配额满等），尝试只保留最新一条
    try {
      if (items.length > 1) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([items[0]]))
      }
    } catch {
      // 彻底无法写入，静默处理
    }
  }
}

/**
 * 截断超大内容，避免单条记录占满存储
 */
function truncateContent(content: unknown): unknown {
  if (content == null) return content
  const str = typeof content === 'string' ? content : JSON.stringify(content)
  if (str.length <= MAX_CONTENT_LENGTH) return content
  return str.slice(0, MAX_CONTENT_LENGTH) + '\n... [内容已截断，共 ' + str.length + ' 字符]'
}

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

/**
 * 工具调试历史 composable
 */
export function useToolDebugHistory() {
  const historyList = ref<ToolDebugHistoryItem[]>(loadFromStorage())

  /**
   * 添加调试历史记录
   */
  function addHistory(params: {
    toolId: string | number
    toolName: string
    category: string
    input: Record<string, unknown>
    result: ToolDebugResultVO
  }): ToolDebugHistoryItem {
    const entry: ToolDebugHistoryItem = {
      id: generateId(),
      toolId: params.toolId,
      toolName: params.toolName,
      category: params.category,
      input: params.input,
      result: {
        ...params.result,
        content: truncateContent(params.result.content)
      },
      executedAt: params.result.executedAt
    }
    historyList.value.unshift(entry)
    if (historyList.value.length > MAX_HISTORY) {
      historyList.value = historyList.value.slice(0, MAX_HISTORY)
    }
    saveToStorage(historyList.value)
    return entry
  }

  /**
   * 删除单条记录
   */
  function removeHistory(id: string): void {
    historyList.value = historyList.value.filter(item => item.id !== id)
    saveToStorage(historyList.value)
  }

  /**
   * 清空所有历史
   */
  function clearHistory(): void {
    historyList.value = []
    saveToStorage(historyList.value)
  }

  return { historyList, addHistory, removeHistory, clearHistory }
}

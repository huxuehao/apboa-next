import { ref } from 'vue'
import type { McpDebugHistoryItem, McpToolDebugResultVO } from '@/types'

/**
 * MCP 工具调试历史管理
 * 仅保存在当前页面内存中，按时间倒序。
 * 调试参数和结果可能包含密钥或业务数据，禁止写入浏览器持久化存储，
 * 避免退出登录或切换租户后被其他用户读取。
 */
/** 最多保留条数 */
const MAX_HISTORY = 20
/** 单条结果内容存储上限（字符数），超出则截断 */
const MAX_CONTENT_LENGTH = 8000

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
 * MCP 调试历史 composable
 */
export function useMcpDebugHistory() {
  const historyList = ref<McpDebugHistoryItem[]>([])

  /**
   * 添加调试历史记录
   */
  function addHistory(params: {
    toolId: string | number
    toolName: string
    serverName: string
    input: Record<string, unknown>
    result: McpToolDebugResultVO
  }): McpDebugHistoryItem {
    const entry: McpDebugHistoryItem = {
      id: generateId(),
      toolId: params.toolId,
      toolName: params.toolName,
      serverName: params.serverName,
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
    return entry
  }

  /**
   * 删除单条记录
   */
  function removeHistory(id: string): void {
    historyList.value = historyList.value.filter(item => item.id !== id)
  }

  /**
   * 清空所有历史
   */
  function clearHistory(): void {
    historyList.value = []
  }

  return { historyList, addHistory, removeHistory, clearHistory }
}

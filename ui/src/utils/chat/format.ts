/**
 * 尝试将字符串格式化为 JSON（美化），失败则返回原文
 */
export function formatToolDisplay(text: string): string {
  if (!text) return text
  const trimmed = text.trim()
  if (!trimmed) return text
  if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
    try {
      const parsed = JSON.parse(trimmed)
      return JSON.stringify(parsed, null, 2)
    } catch {
      return text
    }
  }
  return text
}

/**
 * 构建工具调用的 JSON 内容（用于保存到消息中）
 */
export function buildToolCallsContent(
  toolCalls: Array<{ id: string; name: string; args: string; result?: string; elapsed?: number }>
): string {
  if (toolCalls.length === 0) return ''

  const t = toolCalls[0]!
  const toolContent: Record<string, unknown> = {
    name: t.name,
    totalTimes: t.elapsed ?? 0,
    args: t.args ?? '',
    result: t.result ?? ''
  }

  return JSON.stringify(toolContent)
}

/**
 * 工具耗时格式化：<1s 用 ms（整数），≥1s 用 s（保留两位）。
 * 兼容字符串输入（历史 TOOL 消息经保存链路后 totalTimes 为字符串，如 "2052"）
 */
export function formatElapsed(ms: number | string): string {
  const n = Number(ms)
  if (!Number.isFinite(n) || n < 0) return ''
  if (n < 1000) return `${Math.round(n)}ms`
  return `${(n / 1000).toFixed(2)}s`
}

/**
 * 根据用户输入生成会话标题（截取前50字符）
 */
export function formatSessionTitle(input: string | null): string {
  let t = (input || '').trim()
  if (!t) return '新对话'

  t = t.replace(/<\/?(?:workspace-file|agent-tool|agent-skill)>/g, '')

  return t.length > 50 ? t.slice(0, 50) + '...' : t
}

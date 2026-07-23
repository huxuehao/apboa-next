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

/** 本地时间 yyyy-MM-dd HH:mm:ss（跟后端 jackson 默认序列化一致，流式落定的消息前端先补时间） */
export function localNowDateTime(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 兼容 "2026-05-18 10:27:35" 跟 ISO "2026-05-18T10:27:35"，按本地时区解析 */
export function parseTime(s: string | undefined | null): Date | null {
  if (!s) return null
  const d = new Date(String(s).replace(' ', 'T'))
  return Number.isNaN(d.getTime()) ? null : d
}

/** 完整时间字符串（用于 tooltip）"2026-05-18 19:34:01" */
export function fmtFullTime(s: string | undefined | null): string {
  if (!s) return ''
  const d = parseTime(s)
  if (!d) return String(s)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 相对时间："刚刚 / X 分钟前 / X 小时前 / 昨天 HH:mm / 周X HH:mm / 上周X HH:mm / MM-DD HH:mm / YYYY-MM-DD"。
 *
 * 核心：本周 / 上周用 ISO 周（周一开始）语义化区分，避免"今天周三看见'周五'分不清是本周五还是上周五"的歧义。
 */
export function fmtRelativeTime(s: string | undefined | null): string {
  const d = parseTime(s)
  if (!d) return ''
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 30) return '刚刚'
  if (diffSec < 60) return `${diffSec} 秒前`
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24 && now.getDate() === d.getDate()) return `${diffHour} 小时前`

  const pad = (n: number) => String(n).padStart(2, '0')
  const HHmm = `${pad(d.getHours())}:${pad(d.getMinutes())}`

  // 昨天
  const y = new Date(now)
  y.setDate(y.getDate() - 1)
  if (y.getFullYear() === d.getFullYear() && y.getMonth() === d.getMonth() && y.getDate() === d.getDate()) {
    return `昨天 ${HHmm}`
  }

  // 本周一 00:00:00（ISO 周一开始：getDay() 0=周日..6=周六，daysSinceMonday = (getDay+6) % 7）
  const todayMidnight = new Date(now)
  todayMidnight.setHours(0, 0, 0, 0)
  const thisMonday = new Date(todayMidnight)
  thisMonday.setDate(thisMonday.getDate() - ((now.getDay() + 6) % 7))
  const lastMonday = new Date(thisMonday)
  lastMonday.setDate(lastMonday.getDate() - 7)

  const dayShort = ['日', '一', '二', '三', '四', '五', '六']
  if (d >= thisMonday) return `周${dayShort[d.getDay()]} ${HHmm}`
  if (d >= lastMonday) return `上周${dayShort[d.getDay()]} ${HHmm}`

  if (d.getFullYear() === now.getFullYear()) {
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${HHmm}`
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
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

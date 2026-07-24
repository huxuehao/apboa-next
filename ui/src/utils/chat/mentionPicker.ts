import type { AgentMcpToolItem, MentionResourceItem } from '@/types/chat-mention'

const DEFAULT_RESULT_LIMIT = 30

function normalized(value: string | undefined): string {
  return (value || '')
    .toLocaleLowerCase()
    .replace(/[_\-./:]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function mcpServerName(item: MentionResourceItem): string {
  if (item.kind !== 'agent-mcp') return ''
  return (item.raw as AgentMcpToolItem | undefined)?.serverName || ''
}

function mcpServerId(item: MentionResourceItem): string {
  if (item.kind !== 'agent-mcp') return ''
  return (item.raw as AgentMcpToolItem | undefined)?.serverId || ''
}

function fieldScore(field: string, token: string, weight: number): number {
  if (!field) return -1
  if (field === token) return weight + 300
  if (field.startsWith(token)) return weight + 200
  if (field.split(' ').some((part) => part.startsWith(token))) return weight + 100
  if (field.includes(token)) return weight
  return -1
}

function searchScore(item: MentionResourceItem, tokens: string[]): number | null {
  const name = normalized(item.name)
  const alias = normalized(item.alias)
  const server = normalized(mcpServerName(item))
  const description = normalized(item.description)
  let total = 0

  for (const token of tokens) {
    const best = Math.max(
      fieldScore(name, token, 800),
      fieldScore(alias, token, 760),
      fieldScore(server, token, 440),
      fieldScore(description, token, 180),
    )
    if (best < 0) return null
    total += best
  }
  return total
}

/**
 * 在全部 @ 资源中按名称、别名、MCP 服务名、描述搜索。
 * 多个关键词必须全部命中；名称匹配优先于服务名和描述，最近使用只做轻量加权。
 */
export function searchMentionItems(
  items: MentionResourceItem[],
  keyword: string,
  recentKeys: string[] = [],
  limit = DEFAULT_RESULT_LIMIT,
): MentionResourceItem[] {
  const tokens = normalized(keyword).split(' ').filter(Boolean)
  if (tokens.length === 0) return items.slice(0, limit)
  const recentRank = new Map(recentKeys.map((key, index) => [key, recentKeys.length - index]))

  return items
    .map((item, index) => {
      const score = searchScore(item, tokens)
      if (score == null) return null
      return {
        item,
        index,
        score: score + (recentRank.get(mentionItemKey(item)) || 0) * 5,
      }
    })
    .filter((entry): entry is NonNullable<typeof entry> => entry !== null)
    .sort((a, b) => b.score - a.score || a.index - b.index)
    .slice(0, limit)
    .map((entry) => entry.item)
}

/** 同名 MCP 工具用 serverId 区分 UI 身份；标签值仍沿用工具名。 */
export function mentionItemKey(item: MentionResourceItem): string {
  return `${item.kind}:${mcpServerId(item)}:${item.id}`
}

/** 最近选择置顶、去重并限制长度。 */
export function pushMentionRecent(
  currentKeys: string[],
  item: MentionResourceItem,
  limit = 6,
): string[] {
  const key = mentionItemKey(item)
  return [key, ...currentKeys.filter((candidate) => candidate !== key)].slice(0, limit)
}

/** 根据当前可用资源解析最近记录，自动过滤已解绑或删除的项。 */
export function resolveMentionRecents(
  recentKeys: string[],
  items: MentionResourceItem[],
): MentionResourceItem[] {
  const byKey = new Map(items.map((item) => [mentionItemKey(item), item]))
  return recentKeys.flatMap((key) => {
    const item = byKey.get(key)
    return item ? [item] : []
  })
}

/**
 * 成本中心共享格式化：金额（人民币）与 token 数量
 *
 * @author huxuehao
 */

/** 金额展示：>=1 元保留 2 位，小额保留 4 位（单轮成本常在厘级） */
export function formatCny(v: number | null | undefined): string {
  if (v == null) return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return '—'
  return `¥${n >= 1 || n === 0 ? n.toFixed(2) : n.toFixed(4)}`
}

/** token 量：千分位；超过 10 万转 K/M 缩写 */
export function formatTokens(v: number | string | null | undefined): string {
  if (v == null) return '0'
  const n = Number(v)
  if (Number.isNaN(n)) return '0'
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)}M`
  if (n >= 100_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString('en-US')
}

/** 毫秒耗时 → 可读 */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

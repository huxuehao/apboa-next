/**
 * 数据集列元数据缓存（内存 LRU）。
 * 避免每次选中面板都重新执行数据集取列；用内存而非 localStorage：
 * 列结构轻量、页面刷新后自然重取，既省性能又不占用持久存储、不会撑满 localStorage。
 *
 * @author huxuehao
 */

/** 最大缓存条目数，超出按 LRU 淘汰最久未用 */
const MAX_ENTRIES = 50

const cache = new Map<string, string[]>()

/** 读取缓存列；命中则刷新为最近使用（LRU touch） */
export function getCachedColumns(datasetId: string): string[] | undefined {
  const cols = cache.get(datasetId)
  if (cols) {
    cache.delete(datasetId)
    cache.set(datasetId, cols)
  }
  return cols
}

/** 写入缓存列；超出上限淘汰最久未用条目 */
export function setCachedColumns(datasetId: string, columns: string[]): void {
  if (cache.has(datasetId)) cache.delete(datasetId)
  cache.set(datasetId, columns)
  if (cache.size > MAX_ENTRIES) {
    const oldest = cache.keys().next().value
    if (oldest !== undefined) cache.delete(oldest)
  }
}

/** 使指定数据集缓存失效（绑定变更 / 手动刷新时调用） */
export function invalidateCachedColumns(datasetId: string): void {
  cache.delete(datasetId)
}

/**
 * 聊天附件图片工具：图片类型判定 + objectURL 模块级缓存
 *
 * 附件下载接口需要鉴权头，<img> 无法直接引用，统一走 blob -> objectURL。
 * 缓存按附件 id 复用 objectURL（LRU 上限逐出并 revoke），避免同图重复下载与内存泄漏；
 * objectURL 一经登记，所有权归缓存，调用方不得自行 revoke。
 *
 * @author huxuehao
 */

/** 图片扩展名（与 MediaPreview 的判定保持一致） */
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'ico']

/**
 * 判断扩展名是否为图片类型
 */
export function isImageExtension(extension?: string): boolean {
  if (!extension) return false
  return IMAGE_EXTENSIONS.includes(extension.toLowerCase())
}

const MAX_CACHE = 60
const cache = new Map<string, string>()

/**
 * 读取缓存的图片 objectURL（LRU 触达）
 */
export function getCachedAttachUrl(id: string): string | undefined {
  const url = cache.get(id)
  if (url) {
    cache.delete(id)
    cache.set(id, url)
  }
  return url
}

/**
 * 登记附件 id 对应的 objectURL，超出上限时逐出最久未用并 revoke
 */
export function setCachedAttachUrl(id: string, url: string): void {
  const old = cache.get(id)
  if (old && old !== url) {
    URL.revokeObjectURL(old)
  }
  cache.delete(id)
  cache.set(id, url)
  while (cache.size > MAX_CACHE) {
    const oldestKey = cache.keys().next().value
    if (oldestKey === undefined) break
    const oldestUrl = cache.get(oldestKey)
    cache.delete(oldestKey)
    if (oldestUrl) URL.revokeObjectURL(oldestUrl)
  }
}

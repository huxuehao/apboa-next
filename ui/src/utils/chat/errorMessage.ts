/**
 * 运行错误消息翻译层
 *
 * ErrorEvent 的异常 message 由 ChatLogHook 原样落库（如 reactor 重试耗尽的
 * "Retries exhausted: 2/2"），渲染前按已知模式翻译为用户可读的标题与建议；
 * 未匹配模式保持原文，由错误卡片形态兜底。后续发现新的框架错误往
 * ERROR_PATTERNS 里添即可（与工具名映射同一运营思路）。
 *
 * @author huxuehao
 */

export interface FriendlyError {
  /** 用户可读标题 */
  title: string
  /** 处理建议 */
  advice?: string
  /** 是否命中已知模式（未命中时 title 为兜底文案，原文作为正文展示） */
  matched: boolean
}

const ERROR_PATTERNS: Array<{
  pattern: RegExp
  build: (m: RegExpMatchArray) => { title: string; advice: string }
}> = [
  {
    // reactor 重试规范的默认异常文案：agentscope 的模型调用/工具执行经
    // reactor retry 包装，自动重试耗尽后抛 "Retries exhausted: N/N"
    pattern: /Retries exhausted: (\d+)\/(\d+)/,
    build: (m) => ({
      title: `模型服务调用失败（已自动重试 ${m[2]} 次）`,
      advice: '请稍后重试；若频繁出现，请检查模型配置与模型服务状态。'
    })
  }
]

/**
 * 翻译错误消息：命中已知模式返回中文标题与建议，否则返回兜底标题
 */
export function translateErrorMessage(raw?: string): FriendlyError {
  const text = (raw || '').trim()
  if (!text) return { title: '运行出错', matched: false }
  for (const { pattern, build } of ERROR_PATTERNS) {
    const m = text.match(pattern)
    if (m) return { ...build(m), matched: true }
  }
  return { title: '运行出错', matched: false }
}

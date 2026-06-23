/**
 * uip 协议解析工具
 *
 * 提供 uip 代码块提取、JSON 解析、消息构造等纯函数工具。
 *
 * @author huxuehao
 */

import type { UIPMessage, InteractionSubmitPayload, FormInteraction, ChoiceInteraction } from '@/components/markdown/uip/types'

/** uip 代码块的 HTML 正则（renderMarkdown 后 `<pre><code class="language-uip">`） */
const UIP_HTML_REGEX = /<pre[^>]*>\s*<code[^>]*class="[^"]*language-uip[^"]*"[^>]*>([\s\S]*?)<\/code>\s*<\/pre>/gi
/** 原始 markdown 中的 ```uip 匹配（兜底）
 *  使用 \s* 替代 \n 以兼容 LLM 输出可能缺少尾部换行的情况 */
const UIP_MD_REGEX = /```uip\s*\n([\s\S]*?)\s*```/g

/** HTML 实体解码 */
function decodeHtmlEntities(str: string): string {
  return str
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
}

/** 剥离 HTML 标签（语法高亮注入的 span 等） */
function stripHtmlTags(str: string): string {
  return str.replace(/<[^>]*>/g, '')
}

/** uip 代码块外封套正则（从外层 div.md-code-block 到闭合 div，精准移除整个代码块） */
const CODE_BLOCK_WRAPPER_RE = /<div class="md-code-block"(?!-html)[^>]*>/g

/**
 * 从渲染后 HTML 中提取 uip 代码块
 */
export function extractUIPBlocks(
  html: string
): Array<{ placeholder: string; code: string; fullMatch: string }> {
  const blocks: Array<{ placeholder: string; code: string; fullMatch: string }> = []
  const regex = new RegExp(UIP_HTML_REGEX.source, UIP_HTML_REGEX.flags)
  let match: RegExpExecArray | null
  let idx = 0

  while ((match = regex.exec(html)) !== null) {
    const preBlock = match[0]
    const preStart = match.index
    const preEnd = preStart + preBlock.length

    // 向前查找外层 <div class="md-code-block" 封套（排除 -html 变体）
    const beforeText = html.substring(0, preStart)
    CODE_BLOCK_WRAPPER_RE.lastIndex = 0
    let wrapperMatch: RegExpExecArray | null
    let lastWrapperStart = -1
    while ((wrapperMatch = CODE_BLOCK_WRAPPER_RE.exec(beforeText)) !== null) {
      lastWrapperStart = wrapperMatch.index
    }

    let fullMatch: string
    if (lastWrapperStart !== -1) {
      // 向后查找对应的闭合标签
      const afterText = html.substring(preEnd)
      const closingIdx = afterText.indexOf('</div>')
      if (closingIdx !== -1) {
        fullMatch = html.substring(lastWrapperStart, preEnd + closingIdx + 6)
      } else {
        fullMatch = preBlock
      }
    } else {
      fullMatch = preBlock
    }

    const code = stripHtmlTags(decodeHtmlEntities((match[1] || '').trim()))
    blocks.push({ placeholder: `__UIP_PLACEHOLDER_${idx}__`, code, fullMatch })
    idx++
  }
  return blocks
}

/**
 * 安全解析 UIP JSON，失败返回 null
 */
export function parseUIPJson(code: string): UIPMessage | null {
  try {
    const parsed = JSON.parse(code)
    if (
      parsed &&
      typeof parsed === 'object' &&
      parsed.interaction &&
      !Array.isArray(parsed.interaction)
    ) {
      return parsed as UIPMessage
    }
    return null
  } catch {
    return null
  }
}

/**
 * 构造用户交互提交消息的 content 字段
 * 格式参考 ChatLogHook.getLongTextContent: {"reasoning":"","content":"..."}
 */
export function buildInteractionContent(uipJson: string): string {
  return JSON.stringify({ reasoning: '', content: uipJson })
}

/**
 * 将用户交互数据转为自然语言消息文本（发送给 agent 继续对话）
 * 优先使用 uip 中定义的 label，无 label 时回退为原始字段名/值
 */
export function buildUserTextFromPayload(payload: InteractionSubmitPayload): string {
  const { type, data, uipCode } = payload
  if (type === 'confirm') {
    const { confirmed } = data
    return confirmed ? '已确认' : '已取消'
  }

  // 解析 uipCode 获取 label 映射
  const uip = parseUIPJson(uipCode)

  if (type === 'choice') {
    const d = data as { values: string[]; customInput?: string }
    const parts: string[] = []
    // 优先用选项 label
    if (uip) {
      const interaction = uip.interaction as ChoiceInteraction
      const optionMap = new Map(interaction.options?.map(o => [o.value, o.label]) || [])
      for (const v of d.values) {
        parts.push(optionMap.get(v) || v)
      }
    } else {
      parts.push(...d.values)
    }
    if (d.customInput) parts.push(d.customInput)
    return `已选择：${parts.join('，')}`
  }

  if (type === 'form') {
    // 构建 name→label 映射，以及 select/radio 的 value→label 映射
    const labelMap: Map<string, string> = new Map()
    const optionMaps: Map<string, Map<string, string>> = new Map()
    if (uip) {
      const interaction = uip.interaction as FormInteraction
      for (const f of interaction.fields || []) {
        labelMap.set(f.name, f.label)
        if (f.options && f.options.length > 0) {
          optionMaps.set(f.name, new Map(f.options.map(o => [String(o.value), o.label])))
        }
      }
    }

    const entries = Object.entries(data)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([name, value]) => {
        const displayLabel = labelMap.get(name) || name
        // 数组值用逗号拼接（checkbox-group）
        if (Array.isArray(value)) {
          const optMap = optionMaps.get(name)
          const labelValues = value.map((v: unknown) => optMap ? (optMap.get(String(v)) || String(v)) : String(v))
          return `${displayLabel}=${labelValues.join('、')}`
        }
        // 单值：有选项映射则用 label，否则直接用值
        const optMap = optionMaps.get(name)
        const displayValue = optMap ? (optMap.get(String(value)) || String(value)) : String(value)
        return `${displayLabel}=${displayValue}`
      })
    if (entries.length === 0) return '已提交表单'
    return `已填写表单：${entries.join(', ')}`
  }
  return ''
}

/**
 * 从保存的消息 content 中恢复 UIP 代码块字符串
 * content 格式: {"reasoning":"","content":"```uip\\n{...}\\n```"}
 */
export function extractUIPFromSavedContent(content: string): string | null {
  try {
    const parsed = JSON.parse(content)
    if (parsed && typeof parsed === 'object' && parsed.content) {
      const inner = parsed.content as string
      const match = UIP_MD_REGEX.exec(inner)
      UIP_MD_REGEX.lastIndex = 0
      if (match) return match[1] ?? null
      if (inner.trim().startsWith('{')) return inner.trim()
    }
    return null
  } catch {
    return null
  }
}

/**
 * 将用户提交数据回填到原始 UIP JSON 中
 */
export function fillUIPWithUserData(
  uipCode: string,
  _interactionId: string,
  data: Record<string, unknown>
): string {
  const msg = parseUIPJson(uipCode)
  if (!msg || !msg.interaction) return uipCode

  const interaction = msg.interaction
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const interactionAny = interaction as any

  if (interaction.type === 'form') {
    interactionAny.submittedData = data
    if (interaction.fields) {
      for (const field of interactionAny.fields) {
        if (data[field.name] !== undefined) {
          field.defaultValue = data[field.name]
        }
      }
    }
  } else if (interaction.type === 'choice') {
    interactionAny.submittedData = data
  } else if (interaction.type === 'confirm') {
    interactionAny.submittedData = data
  }

  return JSON.stringify(msg)
}

/**
 * 在 raw markdown content 中定位 ```uip 代码块，对匹配 interactionId 的块注入 submittedData
 *
 * @param content       原始 markdown 消息内容
 * @param interactionId 目标 UIP 交互组件 ID
 * @param submittedData 用户提交的数据
 * @returns 注入 submittedData 后的 content，未匹配时返回原 content
 */
export function injectSubmissionToRawContent(
  content: string,
  interactionId: string,
  submittedData: Record<string, unknown>
): string {
  UIP_MD_REGEX.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = UIP_MD_REGEX.exec(content)) !== null) {
    const fullBlock = match[0]
    const blockStart = match.index
    const jsonStr = (match[1] || '').trim()
    const uip = parseUIPJson(jsonStr)
    if (!uip || !uip.interaction) continue

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const interaction = uip.interaction as any
    if (interaction.id !== interactionId) continue

    try {
      const updatedJson = fillUIPWithUserData(jsonStr, interactionId, submittedData)
      const updatedBlock = wrapUIPBlock(updatedJson)
      // 子串拼接精准替换，避免相同文本的误替换
      return content.substring(0, blockStart) + updatedBlock + content.substring(blockStart + fullBlock.length)
    } catch {
      continue
    }
  }

  return content
}

/**
 * 从文本中剔除 uip 代码块
 */
export function stripUIPBlock(text: string): string {
  UIP_MD_REGEX.lastIndex = 0
  return text.replace(UIP_MD_REGEX, '').trim()
}

/**
 * 判断文本是否包含 uip 代码块
 */
export function hasUIPBlock(text: string): boolean {
  UIP_MD_REGEX.lastIndex = 0
  return UIP_MD_REGEX.test(text)
}

/**
 * 构造 uip 代码块字符串（含标记）
 */
export function wrapUIPBlock(json: string): string {
  return '```uip\n' + json + '\n```'
}

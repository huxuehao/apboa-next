/**
 * 常见模型官网价快照（元 / 百万 token，人民币），供模型配价「按官网价填充」用。
 *
 * 仅作填充起点——供应商随时调价，填充后请与官网核对。匹配规则：modelId
 * 小写后按 prefix 前缀匹配，取最长命中。带阶梯计价的模型取最低档原价、
 * 限时折扣不计入（qwen 定价页 2026-07-16 核对：https://help.aliyun.com/zh/model-studio/model-pricing）。
 *
 * @author huxuehao
 */

export interface OfficialPrice {
  /** modelId 前缀（小写） */
  prefix: string
  /** 输入单价（元/百万token） */
  input: number
  /** 输出单价（元/百万token） */
  output: number
}

export const OFFICIAL_PRICES: OfficialPrice[] = [
  // ── 阿里 DashScope / 通义千问 ──
  // qwen3.7 系列（2026-07-16 官网核对；官网限时折扣中：max 5折/plus 8折，此处按原价）
  // qwen3.7-max 单一档（≤1M）思考/非思考同价；qwen3.7-plus 取 ≤256K 首档（256K~1M 为 6/24）
  { prefix: 'qwen3.7-max', input: 12, output: 36 },
  { prefix: 'qwen3.7-plus', input: 2, output: 8 },
  // qwen3-max 取 ≤32K 首档（32K~128K 为 4/16、128K~256K 为 7/28），2026-07-16 官网核对
  { prefix: 'qwen3-max', input: 2.5, output: 10 },
  { prefix: 'qwen3-plus', input: 0.8, output: 2 },
  { prefix: 'qwen-max', input: 2.4, output: 9.6 },
  { prefix: 'qwen-plus', input: 0.8, output: 2 },
  { prefix: 'qwen-turbo', input: 0.3, output: 0.6 },
  { prefix: 'qwen-long', input: 0.5, output: 2 },
  { prefix: 'qwen-vl-max', input: 3, output: 9 },
  { prefix: 'qwen-vl-plus', input: 1.5, output: 4.5 },
  { prefix: 'qwen-audio-turbo', input: 2, output: 6 },
  { prefix: 'qwq-plus', input: 1.6, output: 4 },
  // ── DeepSeek ──
  { prefix: 'deepseek-chat', input: 2, output: 8 },
  { prefix: 'deepseek-v3', input: 2, output: 8 },
  { prefix: 'deepseek-reasoner', input: 4, output: 16 },
  { prefix: 'deepseek-r1', input: 4, output: 16 },
  // ── 月之暗面 Kimi ──
  { prefix: 'moonshot-v1-8k', input: 12, output: 12 },
  { prefix: 'moonshot-v1-32k', input: 24, output: 24 },
  { prefix: 'moonshot-v1-128k', input: 60, output: 60 },
  { prefix: 'kimi-latest', input: 10, output: 30 },
  // ── 智谱 GLM ──
  { prefix: 'glm-4-plus', input: 50, output: 50 },
  { prefix: 'glm-4-air', input: 0.5, output: 0.5 },
  { prefix: 'glm-4-flash', input: 0, output: 0 },
  // ── 豆包 ──
  { prefix: 'doubao-pro', input: 0.8, output: 2 },
  { prefix: 'doubao-lite', input: 0.3, output: 0.6 }
]

/**
 * 按 modelId 匹配官网价：小写前缀匹配、最长命中优先；未收录返回 null
 */
export function matchOfficialPrice(modelId: string | undefined | null): OfficialPrice | null {
  if (!modelId) return null
  const id = modelId.toLowerCase()
  let best: OfficialPrice | null = null
  for (const p of OFFICIAL_PRICES) {
    if (id.startsWith(p.prefix) && (!best || p.prefix.length > best.prefix.length)) {
      best = p
    }
  }
  return best
}

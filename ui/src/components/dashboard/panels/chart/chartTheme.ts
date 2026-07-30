/**
 * 图表精致化主题：统一配色板、字体、坐标轴/网格/tooltip/图例样式，并按容器尺寸分档自适应。
 * 全局一致的"精致基座"，遵循简约扁平规范（纯色、无渐变/霓虹，阴影仅 tooltip 轻投影）。
 *
 * @author huxuehao
 */

export type SizeTier = 'sm' | 'md' | 'lg'

const FONT =
  'system-ui, -apple-system, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif'

/** 三套扁平现代配色主题：默认(均衡) / 活力(明快) / 沉稳(低饱和) */
const DEFAULT_PALETTE = [
  '#3b82f6',
  '#22c55e',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#06b6d4',
  '#ec4899',
  '#14b8a6',
]

export const CHART_PALETTES: Record<string, string[]> = {
  default: DEFAULT_PALETTE,
  vivid: ['#4f46e5', '#06b6d4', '#f97316', '#e11d48', '#a855f7', '#0ea5e9', '#f43f5e', '#10b981'],
  calm: ['#5b8ff9', '#61ddaa', '#65789b', '#f6bd16', '#7262fd', '#78d3f8', '#9661bc', '#f6903d'],
}

export function getPalette(scheme?: string): string[] {
  return CHART_PALETTES[scheme || 'default'] || DEFAULT_PALETTE
}

/** 依据容器宽高判定尺寸档位，用于自适应字号/间距/符号大小 */
export function sizeTier(w: number, h: number): SizeTier {
  const width = w || 0
  const height = h || 0
  if (width < 300 || height < 200) return 'sm'
  if (width >= 560 && height >= 320) return 'lg'
  return 'md'
}

export function axisLabelFont(t: SizeTier): number {
  return t === 'sm' ? 10 : t === 'lg' ? 13 : 12
}

export function baseTextStyle() {
  return { fontFamily: FONT, color: '#595959' }
}

/** tooltip：白底、细边、圆角、轻投影（浮层，非卡片装饰） */
export function tooltipStyle() {
  return {
    backgroundColor: '#ffffff',
    borderColor: '#f0f0f0',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: '#1a1a1a', fontSize: 12, fontFamily: FONT },
    extraCssText: 'box-shadow: 0 4px 16px rgba(0,0,0,0.08); border-radius: 8px;',
  }
}

/** 图例：圆角小色块、留白紧凑、柔和文字色 */
export function legendStyle(t: SizeTier) {
  return {
    icon: 'roundRect',
    itemWidth: 10,
    itemHeight: 10,
    itemGap: t === 'sm' ? 8 : 12,
    textStyle: { color: '#8c8c8c', fontSize: t === 'sm' ? 10 : 12, fontFamily: FONT },
  }
}

/** 分类轴：底线柔和、无刻度、标签防重叠 */
export function categoryAxisStyle(t: SizeTier) {
  return {
    axisLine: { lineStyle: { color: '#e8e8e8' } },
    axisTick: { show: false },
    axisLabel: {
      color: '#8c8c8c',
      fontSize: axisLabelFont(t),
      fontFamily: FONT,
      hideOverlap: true,
    },
  }
}

/** 数值轴：隐藏轴线、虚线分隔、柔和标签 */
export function valueAxisStyle(t: SizeTier) {
  return {
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
    axisLabel: { color: '#8c8c8c', fontSize: axisLabelFont(t), fontFamily: FONT },
  }
}

/** 网格内边距：按档位与图例位置动态收紧 */
export function gridPad(t: SizeTier, legend: { top?: boolean; bottom?: boolean }) {
  return {
    left: 8,
    right: 12,
    top: legend.top ? (t === 'sm' ? 24 : 30) : t === 'sm' ? 10 : 14,
    bottom: legend.bottom ? (t === 'sm' ? 22 : 28) : 4,
    containLabel: true,
  }
}

export function symbolSize(t: SizeTier): number {
  return t === 'sm' ? 4 : t === 'lg' ? 7 : 5
}

/**
 * 图表 option 构建器：按图表类型注册 builder（策略注册表）。
 * 统一套用精致主题（chartTheme），按容器尺寸自适应，并读取面板语义开关；
 * 仍支持通过 panel.options.echarts 深度覆盖（高级逃生舱）。
 *
 * @author huxuehao
 */
import { cloneDeep, merge } from 'lodash-es'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'
import {
  axisLabelFont,
  baseTextStyle,
  categoryAxisStyle,
  getPalette,
  gridPad,
  legendStyle,
  sizeTier,
  symbolSize,
  tooltipStyle,
  valueAxisStyle,
  type SizeTier,
} from './chartTheme'

type ChartOption = Record<string, unknown>
/** 渲染上下文：容器尺寸档位 */
interface BuildCtx {
  tier: SizeTier
}
type ChartBuilder = (panel: PanelDsl, data: DatasetExecuteResult | null, ctx: BuildCtx) => ChartOption

const builders = new Map<string, ChartBuilder>()

export function registerChartBuilder(type: string, builder: ChartBuilder) {
  builders.set(type, builder)
}

export function hasChartBuilder(type: string): boolean {
  return builders.has(type)
}

/**
 * 构建最终 echarts option：精致基座 + 类型 builder + 用户深度覆盖
 */
export function buildChartOption(
  panel: PanelDsl,
  data: DatasetExecuteResult | null,
  ctx?: { width: number; height: number },
): ChartOption {
  const tier = ctx ? sizeTier(ctx.width, ctx.height) : 'md'
  const builder = builders.get(panel.type)
  const base = builder ? builder(panel, data, { tier }) : {}
  // 全局精致基座：配色板 + 统一字体
  base.color = getPalette(panel.options?.colorScheme as string | undefined)
  base.textStyle = baseTextStyle()
  const override = (panel.options?.echarts as ChartOption) || {}
  return merge(cloneDeep(base), cloneDeep(override))
}

function toArray(value: unknown): string[] {
  if (Array.isArray(value)) return value as string[]
  return value ? [value as string] : []
}

/** 读取图例显隐/位置 */
function resolveLegend(panel: PanelDsl, seriesCount: number, tier: SizeTier) {
  const opts = panel.options || {}
  const show = opts.showLegend !== undefined ? !!opts.showLegend : seriesCount > 1
  const atBottom = opts.legendPosition === 'bottom'
  const legend = {
    show,
    ...legendStyle(tier),
    ...(atBottom ? { bottom: 0, left: 'center' } : { top: 0, left: 'center' }),
  }
  return { legend, hasTop: show && !atBottom, hasBottom: show && atBottom }
}

/** 直角坐标系图表（折线/柱状/散点/面积） */
function cartesianBuilder(kind: 'line' | 'bar' | 'scatter' | 'area'): ChartBuilder {
  return (panel, data, { tier }) => {
    const opts = panel.options || {}
    const rows = data?.rows || []
    const mapping = panel.fieldMapping || {}
    const xField = mapping.x as string | undefined
    const yFields = toArray(mapping.y)
    const categories = xField ? rows.map((r) => r[xField]) : rows.map((_, i) => i + 1)

    const realType = kind === 'area' ? 'line' : kind
    const isLine = realType === 'line'
    const isBar = kind === 'bar'
    const horizontal = isBar && !!opts.horizontal

    const series = yFields.map((yf) => {
      const s: Record<string, unknown> = {
        name: yf,
        type: realType,
        data: rows.map((r) => r[yf]),
      }
      if (opts.stack && (isBar || isLine)) s.stack = 'total'
      if (opts.showLabel) {
        s.label = {
          show: true,
          fontSize: axisLabelFont(tier),
          color: '#595959',
          position: horizontal ? 'right' : 'top',
        }
      }
      if (isLine) {
        s.smooth = !!opts.smooth
        s.showSymbol = tier !== 'sm'
        s.symbol = 'circle'
        s.symbolSize = symbolSize(tier)
        s.lineStyle = { width: Number(opts.lineWidth) || 2 }
        if (kind === 'area') s.areaStyle = { opacity: 0.12 }
      }
      if (isBar) {
        s.barMaxWidth = tier === 'lg' ? 36 : 24
        s.itemStyle = { borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] }
      }
      if (realType === 'scatter') s.symbolSize = symbolSize(tier) + 3
      return s
    })

    const { legend, hasTop, hasBottom } = resolveLegend(panel, yFields.length, tier)
    const catAxis = { type: 'category', data: categories, boundaryGap: realType !== 'line', ...categoryAxisStyle(tier) }
    const valAxis = { type: 'value', ...valueAxisStyle(tier) }

    return {
      tooltip: { trigger: 'axis', ...tooltipStyle(), axisPointer: { type: isBar ? 'shadow' : 'line' } },
      legend,
      grid: gridPad(tier, { top: hasTop, bottom: hasBottom }),
      xAxis: horizontal ? valAxis : catAxis,
      yAxis: horizontal ? catAxis : valAxis,
      series,
    }
  }
}

/** 饼图 */
const pieBuilder: ChartBuilder = (panel, data, { tier }) => {
  const opts = panel.options || {}
  const rows = data?.rows || []
  const mapping = panel.fieldMapping || {}
  const nameField = (mapping.name as string) || (mapping.x as string)
  const valueField = (mapping.value as string) || toArray(mapping.y)[0]
  const seriesData = rows.map((r) => ({
    name: nameField ? String(r[nameField]) : '',
    value: valueField ? r[valueField] : 0,
  }))
  const { legend, hasTop, hasBottom } = resolveLegend(panel, 2, tier)
  const donut = !!opts.donut
  const showLabel = opts.showLabel !== undefined ? !!opts.showLabel : tier !== 'sm'
  const topOffset = hasTop ? 8 : 0
  const bottomOffset = hasBottom ? 8 : 0

  return {
    tooltip: { trigger: 'item', ...tooltipStyle() },
    legend,
    series: [
      {
        type: 'pie',
        radius: donut ? ['42%', '70%'] : ['0%', '68%'],
        center: ['50%', `${50 + topOffset - bottomOffset}%`],
        roseType: opts.rose ? 'radius' : undefined,
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
        label: {
          show: showLabel,
          formatter: '{b} {d}%',
          fontSize: axisLabelFont(tier),
          color: '#595959',
        },
        labelLine: { show: showLabel, length: 8, length2: 8 },
        data: seriesData,
      },
    ],
  }
}

/** 雷达图 */
const radarBuilder: ChartBuilder = (panel, data, { tier }) => {
  const opts = panel.options || {}
  const rows = data?.rows || []
  const mapping = panel.fieldMapping || {}
  const nameField = (mapping.name as string) || (mapping.x as string)
  const yFields = toArray(mapping.y)
  let max = 0
  yFields.forEach((yf) =>
    rows.forEach((r) => {
      const n = Number(r[yf])
      if (!Number.isNaN(n) && n > max) max = n
    }),
  )
  const indicator = rows.map((r) => ({
    name: nameField ? String(r[nameField]) : '',
    max: max > 0 ? max : undefined,
  }))
  const series = [
    {
      type: 'radar',
      symbolSize: symbolSize(tier),
      ...(opts.area ? { areaStyle: { opacity: 0.12 } } : {}),
      lineStyle: { width: 2 },
      data: yFields.map((yf) => ({
        name: yf,
        value: rows.map((r) => {
          const n = Number(r[yf])
          return Number.isNaN(n) ? null : n
        }),
      })),
    },
  ]
  const { legend } = resolveLegend(panel, yFields.length, tier)
  return {
    tooltip: { trigger: 'item', ...tooltipStyle() },
    legend,
    radar: {
      indicator,
      axisName: { color: '#8c8c8c', fontSize: axisLabelFont(tier) },
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      splitArea: { areaStyle: { color: ['rgba(0,0,0,0.01)', 'rgba(0,0,0,0.03)'] } },
    },
    series,
  }
}

registerChartBuilder('line', cartesianBuilder('line'))
registerChartBuilder('area', cartesianBuilder('area'))
registerChartBuilder('bar', cartesianBuilder('bar'))
registerChartBuilder('scatter', cartesianBuilder('scatter'))
registerChartBuilder('pie', pieBuilder)
registerChartBuilder('radar', radarBuilder)

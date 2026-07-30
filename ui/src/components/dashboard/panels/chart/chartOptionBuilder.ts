/**
 * 图表 option 构建器：按图表类型注册 builder（策略注册表）。
 * 新增图表类型只需注册一个 builder，并支持通过 panel.options.echarts 深度覆盖。
 *
 * @author huxuehao
 */
import { cloneDeep, merge } from 'lodash-es'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'

type ChartOption = Record<string, unknown>
type ChartBuilder = (panel: PanelDsl, data: DatasetExecuteResult | null) => ChartOption

const builders = new Map<string, ChartBuilder>()

export function registerChartBuilder(type: string, builder: ChartBuilder) {
  builders.set(type, builder)
}

export function hasChartBuilder(type: string): boolean {
  return builders.has(type)
}

/**
 * 构建最终 echarts option：基础 option 深度合并用户覆盖
 */
export function buildChartOption(panel: PanelDsl, data: DatasetExecuteResult | null): ChartOption {
  const builder = builders.get(panel.type)
  const base = builder ? builder(panel, data) : {}
  const override = (panel.options?.echarts as ChartOption) || {}
  return merge(cloneDeep(base), cloneDeep(override))
}

function toArray(value: unknown): string[] {
  if (Array.isArray(value)) return value as string[]
  return value ? [value as string] : []
}

/** 直角坐标系图表（折线/柱状/散点/面积） */
function cartesianBuilder(seriesType: string, extra?: Record<string, unknown>): ChartBuilder {
  return (panel, data) => {
    const rows = data?.rows || []
    const mapping = panel.fieldMapping || {}
    const xField = mapping.x as string | undefined
    const yFields = toArray(mapping.y)
    const categories = xField ? rows.map((r) => r[xField]) : rows.map((_, i) => i + 1)
    const series = yFields.map((yf) => ({
      name: yf,
      type: seriesType,
      data: rows.map((r) => r[yf]),
      ...(extra || {}),
    }))
    return {
      tooltip: { trigger: 'axis' },
      legend: { show: yFields.length > 1, top: 0 },
      grid: { left: 8, right: 16, top: 28, bottom: 8, containLabel: true },
      xAxis: { type: 'category', data: categories, boundaryGap: seriesType !== 'line' },
      yAxis: { type: 'value' },
      series,
    }
  }
}

/** 饼图 */
const pieBuilder: ChartBuilder = (panel, data) => {
  const rows = data?.rows || []
  const mapping = panel.fieldMapping || {}
  const nameField = (mapping.name as string) || (mapping.x as string)
  const valueField = (mapping.value as string) || toArray(mapping.y)[0]
  const seriesData = rows.map((r) => ({
    name: nameField ? String(r[nameField]) : '',
    value: valueField ? r[valueField] : 0,
  }))
  return {
    tooltip: { trigger: 'item' },
    legend: { show: true, top: 0 },
    series: [{ type: 'pie', radius: ['0%', '62%'], center: ['50%', '55%'], data: seriesData }],
  }
}

/** 雷达图 */
const radarBuilder: ChartBuilder = (panel, data) => {
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
      data: yFields.map((yf) => ({
        name: yf,
        value: rows.map((r) => {
          const n = Number(r[yf])
          return Number.isNaN(n) ? null : n
        }),
      })),
    },
  ]
  return {
    tooltip: { trigger: 'item' },
    legend: { show: yFields.length > 1, top: 0 },
    radar: { indicator },
    series,
  }
}

registerChartBuilder('line', cartesianBuilder('line', { smooth: false }))
registerChartBuilder('area', cartesianBuilder('line', { smooth: false, areaStyle: {} }))
registerChartBuilder('bar', cartesianBuilder('bar'))
registerChartBuilder('scatter', cartesianBuilder('scatter'))
registerChartBuilder('pie', pieBuilder)
registerChartBuilder('radar', radarBuilder)

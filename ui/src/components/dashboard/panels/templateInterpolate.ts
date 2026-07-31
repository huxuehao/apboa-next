/**
 * 文本 / Markdown 面板占位插值。
 * - 标量占位：{{ 字段 }} 取数据集首行对应列的值
 * - 行循环：{{#each}} ... {{ 字段 }} ... {{/each}} 对每一行重复内部模板，块内 {{ 字段 }} 指向当前行
 * 字段缺失或无数据时替换为空串。
 *
 * @author huxuehao
 */
import type { DatasetExecuteResult } from '@/types/dashboard'

const EACH_RE = /\{\{\s*#each\s*\}\}([\s\S]*?)\{\{\s*\/each\s*\}\}/g
// 字段名允许中文等 Unicode（除花括号外任意字符），不能用 \w（仅 ASCII）
const SCALAR_RE = /\{\{\s*([^{}]+?)\s*\}\}/g

/** 取某行某列的字符串值，缺失/空为空串 */
function fieldValue(row: Record<string, unknown> | undefined, key: string): string {
  if (!row) return ''
  const v = row[key]
  return v === null || v === undefined ? '' : String(v)
}

/** 用指定行填充标量占位；遵略遗留的 #each / /each 块标记（避免被当作字段清空） */
function fillScalars(tpl: string, row: Record<string, unknown> | undefined): string {
  return tpl.replace(SCALAR_RE, (match, key: string) => {
    const name = key.trim()
    if (name.startsWith('#') || name.startsWith('/')) return match
    return fieldValue(row, name)
  })
}

/**
 * 将内容中的占位符替换为数据集数据。
 * 先展开行循环块（对每一行重复内部模板），再处理顶层标量占位（取首行）。
 */
export function interpolateTemplate(content: string, data: DatasetExecuteResult | null): string {
  if (!content) return ''
  const rows = data?.rows || []
  const expanded = content.replace(EACH_RE, (_m, inner: string) =>
    rows.map((row) => fillScalars(inner, row)).join(''),
  )
  return fillScalars(expanded, rows[0])
}

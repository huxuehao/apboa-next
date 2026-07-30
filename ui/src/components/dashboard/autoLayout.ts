/**
 * 面板自动排版：按整数栅格重算各面板 x/y，保留各自宽高。
 * - compact 紧凑：保留列位置与相对顺序，向上重力压缩去除垂直空隙
 * - tidy   平铺：按阅读顺序从左到右流式排布，行满换行
 *
 * @author huxuehao
 */
import type { PanelDsl } from '@/types/dashboard'

export type AutoLayoutMode = 'compact' | 'tidy'

/** 就地重排面板布局（会替换每个面板的 layout 为新对象） */
export function applyAutoLayout(panels: PanelDsl[], cols: number, mode: AutoLayoutMode): void {
  const ordered = [...panels].sort(
    (a, b) => a.layout.y - b.layout.y || a.layout.x - b.layout.x,
  )
  if (mode === 'tidy') {
    tidy(ordered, cols)
  } else {
    compact(ordered, cols)
  }
}

/** 紧凑（skyline 最佳填充）：按阅读顺序，每个面板放入当前高度最低的可用位置，填满空洞、左上紧贴 */
function compact(list: PanelDsl[], cols: number): void {
  const heights = new Array(cols).fill(0)
  for (const p of list) {
    const w = Math.min(p.layout.w, cols)
    let bestX = 0
    let bestY = Infinity
    // 扫描所有可放起始列，选取落点 y 最小的位置（平手取最左）
    for (let x = 0; x <= cols - w; x++) {
      let y = 0
      for (let c = x; c < x + w; c++) y = Math.max(y, heights[c])
      if (y < bestY) {
        bestY = y
        bestX = x
      }
    }
    p.layout = { x: bestX, y: bestY, w, h: p.layout.h }
    for (let c = bestX; c < bestX + w; c++) heights[c] = bestY + p.layout.h
  }
}

/** 流式平铺：从左到右依次摆放，超出列数换行，保留各自宽高 */
function tidy(list: PanelDsl[], cols: number): void {
  let cursorX = 0
  let rowY = 0
  let rowH = 0
  for (const p of list) {
    const w = Math.min(p.layout.w, cols)
    if (cursorX + w > cols) {
      rowY += rowH
      cursorX = 0
      rowH = 0
    }
    p.layout = { x: cursorX, y: rowY, w, h: p.layout.h }
    cursorX += w
    rowH = Math.max(rowH, p.layout.h)
  }
}

/**
 * pagedjs 0.4.x 最小类型声明（官方包未附带 TS 类型）
 * 仅声明本项目用到的 Previewer.preview。
 */
declare module 'pagedjs' {
  export interface PagedFlow {
    total: number
    performance?: number
    pages?: unknown[]
  }

  /** stylesheets 数组元素：URL 字符串，或 { _: cssText } 内联样式对象 */
  export type PagedStylesheet = string | { _: string }

  export class Previewer {
    constructor(options?: Record<string, unknown>)
    /**
     * @param content  HTML 字符串或 DOM 节点
     * @param stylesheets  样式表数组（URL 或内联 CSS）
     * @param renderTo  渲染目标容器
     */
    preview(
      content: string | HTMLElement,
      stylesheets: PagedStylesheet[],
      renderTo: HTMLElement,
    ): Promise<PagedFlow>
  }

  export class Handler {}
  export function registerHandlers(...handlers: unknown[]): void
}

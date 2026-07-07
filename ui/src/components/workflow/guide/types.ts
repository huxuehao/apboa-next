import type { Component } from 'vue'

/** 指南条目元数据 */
export interface GuideEntry {
  /** 唯一标识 */
  id: string
  /** 侧栏显示标题 */
  title: string
  /** 对应的内容组件 */
  component: Component
}

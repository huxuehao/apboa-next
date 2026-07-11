/**
 * @guide 指南系统
 *
 * 提供工作流画布等场景的使用说明弹窗。
 * 采用"注册中心"模式：新增指南只需追加 content/index.ts 中的条目并编写内容组件。
 *
 * 使用示例：
 * ```
 * import { GuideModal, guideEntries } from '@/components/workflow/guide'
 * // 在模板中：<GuideModal :entries="guideEntries" />
 * ```
 */

export { default as GuideModal } from './GuideModal.vue'
export { default as GuideTrigger } from './GuideTrigger.vue'
export { guideEntries } from './content'
export type { GuideEntry } from './types'

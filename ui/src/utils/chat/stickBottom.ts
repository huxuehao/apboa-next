import type { Directive } from 'vue'

interface StickEl extends HTMLElement {
  _stickBottom?: boolean
  _stickScrollHandler?: () => void
}

/** 判定"贴底"的距离阈值（px），与 ChatMain 页面级自动滚的手感一致 */
const NEAR_BOTTOM_PX = 40

/**
 * 贴底跟随滚动指令：限高滚动容器内流式内容增长时自动滚到底部；
 * 用户向上滚动查看历史即脱离跟随，滚回底部附近自动恢复。
 *
 * 指令值为启用开关（缺省启用）：`v-stick-bottom="isStreaming"`——流式中跟随，
 * 定稿/历史内容不启用（展开历史时应从头阅读，不被拉到底部）。
 * 页面级（正文气泡）滚动由 ChatMain 的 shouldAutoScroll 管理，不用本指令。
 */
export const vStickBottom: Directive<StickEl, boolean | undefined> = {
  mounted(el, binding) {
    el._stickBottom = true
    el._stickScrollHandler = () => {
      el._stickBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= NEAR_BOTTOM_PX
    }
    // 程序化拉底后 scroll 事件重算结果仍为"贴底"，天然自洽，无需区分事件来源
    el.addEventListener('scroll', el._stickScrollHandler, { passive: true })
    if (binding.value !== false) {
      el.scrollTop = el.scrollHeight
    }
  },
  updated(el, binding) {
    if (binding.value !== false && el._stickBottom) {
      el.scrollTop = el.scrollHeight
    }
  },
  unmounted(el) {
    if (el._stickScrollHandler) {
      el.removeEventListener('scroll', el._stickScrollHandler)
    }
  }
}

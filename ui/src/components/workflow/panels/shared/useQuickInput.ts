import { ref, onMounted, onUnmounted } from 'vue'

/** 双击 Ctrl 间隔阈值（毫秒） */
const DOUBLE_PRESS_THRESHOLD = 500

/**
 * 快捷输入组合式逻辑
 * 监听全局双击 Ctrl 事件，在焦点位于配置面板内时弹出输入绑定选择面板
 */
export function useQuickInput() {
  const isPopupVisible = ref(false)
  const popupItems = ref<string[]>([])
  const anchorElement = ref<HTMLElement | null>(null)

  // 双击 Ctrl 检测
  let lastCtrlTime = 0

  // 配置面板的 DOM 选择器
  const PANEL_SELECTOR = '.config-panel'

  /**
   * 设置当前可用的输入绑定名称列表
   */
  function setInputNames(names: string[]) {
    popupItems.value = names
  }

  /**
   * 判断当前焦点元素是否在配置面板内
   */
  function isFocusInsidePanel(): boolean {
    const activeEl = document.activeElement
    if (!activeEl) return false
    return !!activeEl.closest(PANEL_SELECTOR)
  }

  /**
   * 判断当前焦点元素是否为可输入元素
   */
  function isEditableElement(el: Element | null): boolean {
    if (!el) return false
    const tag = el.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA') return true
    if ((el as HTMLElement).isContentEditable) return true
    // CodeMirror 6 的 contenteditable 区域
    if (el.closest('.cm-content')) return true
    return false
  }

  /**
   * 处理全局按键事件
   */
  function onKeyDown(e: KeyboardEvent) {
    const activeEl = document.activeElement

    // 弹窗已打开时不重复触发
    if (isPopupVisible.value) return

    // 非配置面板内或非可输入元素，重置状态
    if (!isFocusInsidePanel() || !isEditableElement(activeEl)) {
      lastCtrlTime = 0
      return
    }

    if (e.key === 'Control' && !e.repeat) {
      // 孤立的 Ctrl 按下
      const now = Date.now()
      if (lastCtrlTime > 0 && now - lastCtrlTime < DOUBLE_PRESS_THRESHOLD) {
        e.preventDefault()
        lastCtrlTime = 0
        triggerQuickInput(activeEl as HTMLElement)
      } else {
        lastCtrlTime = now
      }
    } else if (e.ctrlKey) {
      // Ctrl 组合键（如 Ctrl+C/V），取消待处理的单次 Ctrl 计时
      lastCtrlTime = 0
    }
  }

  /**
   * 触发快捷输入
   */
  function triggerQuickInput(el: HTMLElement) {
    const names = popupItems.value
    if (!names || names.length === 0) return

    // 单个输入绑定：直接插入，不弹窗
    if (names.length === 1) {
      insertTextAtCursor(el, `\${${names[0]}}`)
      return
    }

    // 多个输入绑定：弹出选择面板
    anchorElement.value = el
    isPopupVisible.value = true
  }

  /**
   * 在焦点元素的光标位置插入文本
   */
  function insertTextAtCursor(el: HTMLElement, text: string) {
    // 先聚焦
    el.focus()

    if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
      insertIntoNativeInput(el, text)
    } else {
      // CodeMirror 等 contenteditable 场景，使用 execCommand 兼容
      insertIntoContentEditable(text)
    }
  }

  /**
   * 向原生 input/textarea 插入文本
   */
  function insertIntoNativeInput(el: HTMLInputElement | HTMLTextAreaElement, text: string) {
    const start = el.selectionStart ?? 0
    const end = el.selectionEnd ?? 0
    const value = el.value
    el.value = value.slice(0, start) + text + value.slice(end)
    el.selectionStart = el.selectionEnd = start + text.length
    // 触发 Vue v-model 更新
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }

  /**
   * 向 contenteditable（含 CodeMirror）插入文本
   */
  function insertIntoContentEditable(text: string) {
    document.execCommand('insertText', false, text)
  }

  /**
   * 用户选择了一个输入绑定名称
   */
  function handleSelect(name: string) {
    const el = anchorElement.value
    if (el) {
      insertTextAtCursor(el, `\${${name}}`)
    }
    closePopup()
  }

  /**
   * 关闭弹窗
   */
  function closePopup() {
    isPopupVisible.value = false
    anchorElement.value = null
  }

  onMounted(() => {
    document.addEventListener('keydown', onKeyDown)
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', onKeyDown)
  })

  return {
    isPopupVisible,
    popupItems,
    anchorElement,
    setInputNames,
    handleSelect,
    closePopup,
  }
}

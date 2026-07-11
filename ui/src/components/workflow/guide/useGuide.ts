import { ref, computed } from 'vue'
import type { GuideEntry } from './types'

/**
 * 指南弹窗状态管理
 * 管理弹窗开关与当前选中指南
 */
export function useGuide(entries: GuideEntry[]) {
  const open = ref(false)
  const activeId = ref(entries[0]?.id || '')

  const activeEntry = computed(() => entries.find((e) => e.id === activeId.value))

  function selectGuide(id: string) {
    activeId.value = id
  }

  function toggle(force?: boolean) {
    open.value = force ?? !open.value
  }

  return { open, activeId, activeEntry, selectGuide, toggle }
}

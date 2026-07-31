/**
 * 定时刷新组合式：按配置定时触发回调，页面隐藏时暂停，卸载时清理，避免定时器泄漏。
 *
 * @author huxuehao
 */
import { onBeforeUnmount, onMounted } from 'vue'

interface TimerConfig {
  enabled: boolean
  interval: number
}

export function useRefreshTimer(callback: () => void, config: () => TimerConfig) {
  let timer: ReturnType<typeof setInterval> | null = null

  function clear() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function setup() {
    clear()
    const c = config()
    if (c.enabled && c.interval > 0 && document.visibilityState === 'visible') {
      timer = setInterval(callback, c.interval * 1000)
    }
  }

  function onVisibilityChange() {
    setup()
  }

  onMounted(() => {
    setup()
    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onBeforeUnmount(() => {
    clear()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return { restart: setup, stop: clear }
}

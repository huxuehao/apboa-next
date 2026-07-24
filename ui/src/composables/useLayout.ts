import { ref } from 'vue'
import { useMediaQuery, useStorage } from '@vueuse/core'

/**
 * 布局响应式与侧边栏状态（三组件共享，故置于模块级单例）
 * - isMobile：<1024px 视为移动端，导航改用左侧抽屉
 * - collapsed：桌面端侧边栏是否折叠为 mini（只图标），localStorage 持久化
 * - drawerOpen：移动端抽屉开合
 *
 * @author vaulka
 */

/** 移动端断点：桌面侧边栏窄窗到 1024px 以下也会挤压内容，一并切走抽屉 */
const isMobile = useMediaQuery('(max-width: 1023.98px)')

/** 桌面端侧边栏折叠（mini 只图标），记住用户偏好 */
const collapsed = useStorage('apboa:sidebar:collapsed', false)

/** 移动端抽屉开合 */
const drawerOpen = ref(false)

export function useLayout() {
  /** 切换桌面侧边栏折叠 */
  const toggleCollapsed = () => {
    collapsed.value = !collapsed.value
  }
  /** 打开移动端抽屉 */
  const openDrawer = () => {
    drawerOpen.value = true
  }
  /** 关闭移动端抽屉 */
  const closeDrawer = () => {
    drawerOpen.value = false
  }

  return { isMobile, collapsed, drawerOpen, toggleCollapsed, openDrawer, closeDrawer }
}

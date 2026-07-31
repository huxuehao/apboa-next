/**
 * Dashboard 门户状态
 *
 * @author huxuehao
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardPortal } from '@/api/dashboard'
import type { PortalDashboard } from '@/types/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const portal = ref<PortalDashboard | null>(null)
  const loading = ref(false)

  /**
   * 加载当前用户生效的门户 Dashboard
   */
  async function loadPortal() {
    loading.value = true
    try {
      const resp = await dashboardPortal()
      portal.value = resp.data.data
      return portal.value
    } finally {
      loading.value = false
    }
  }

  return { portal, loading, loadPortal }
})

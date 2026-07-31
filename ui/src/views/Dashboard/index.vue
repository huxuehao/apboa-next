<script setup lang="ts">
/**
 * 工作台门户：按 DSL 动态渲染当前用户生效的 Dashboard。
 *
 * @author huxuehao
 */
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { EditOutlined } from '@ant-design/icons-vue'
import { useDashboardStore } from '@/stores'
import { registerBuiltinPanels } from '@/components/dashboard/panels'
import DashboardGrid from '@/components/dashboard/DashboardGrid.vue'
import DashboardEmpty from '@/components/dashboard/DashboardEmpty.vue'
import { RouteNames } from '@/router/constants'

registerBuiltinPanels()

const router = useRouter()
const dashboardStore = useDashboardStore()

const dsl = computed(() => dashboardStore.portal?.config || null)
const hasPanels = computed(() => (dsl.value?.panels?.length || 0) > 0)

function goDesigner() {
  router.push({ name: RouteNames.DASHBOARD_DESIGN })
}

onMounted(() => {
  dashboardStore.loadPortal()
})
</script>

<script lang="ts">
export default {
  name: 'DashboardView',
}
</script>

<template>
  <div class="dashboard-portal">
    <div class="portal-header">
      <div class="portal-intro">
        <h3 class="portal-title">工作台</h3>
        <p class="portal-desc text-secondary">
          工作台是你的专属数据门户，可自由编排图表、指标与表格面板，基于数据集实时呈现关键业务视图。
          进入设计器即可拖拽布局、绑定数据并个性化样式，打造千人千面、属于你的看板。
        </p>
      </div>
      <div class="portal-actions">
        <a-button type="text" @click="goDesigner">
          <template #icon><EditOutlined /></template>
          设计器
        </a-button>
      </div>
    </div>

    <div class="portal-body">
      <a-spin v-if="dashboardStore.loading" />
      <template v-else-if="dsl && hasPanels">
        <DashboardGrid :dsl="dsl" />
      </template>
      <DashboardEmpty v-else @create="goDesigner" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard-portal {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}

.portal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 24px 8px;
}

.portal-intro {
  flex: 1;
  min-width: 0;
}

.portal-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-sm) 0;
}

.portal-desc {
  font-size: var(--font-size-base);
  line-height: 1.6;
  max-width: 800px;
  margin: 0;
}

.portal-actions {
  flex-shrink: 0;
}

.portal-body {
  padding: 16px 10px;
}
</style>

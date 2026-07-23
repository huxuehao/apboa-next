<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
/**
 * 主布局组件 — 新侧边栏 + 顶栏的响应式布局
 * 桌面侧边栏常驻并可折叠，移动端收进左侧抽屉。
 *
 * @author huxuehao
 */
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useAccountStore } from "@/stores";
import { Sidebar, AppTopbar, AppFooter } from '@/components/layout'
import { useLayout } from '@/composables/useLayout'

const route = useRoute();
const { getRefresh } =  useAccountStore()

const isRefresh = computed(() => {
  return getRefresh()
})
/** 路由 meta.hideFooter 为 true 时隐藏底部 */
const showFooter = computed(() => !route.meta.hideFooter)

const { isMobile, collapsed, drawerOpen, closeDrawer } = useLayout()
</script>

<template>
  <div
    class="app-layout flex"
    :class="{ 'is-mobile': isMobile, 'sidebar-collapsed': collapsed && !isMobile }"
  >
    <!-- 桌面：常驻侧边栏 -->
    <Sidebar
      v-if="!isMobile"
      class="layout-sidebar"
    />

    <!-- 移动：侧边栏收进左侧抽屉 -->
    <ADrawer
      v-if="isMobile"
      :open="drawerOpen"
      placement="left"
      :closable="false"
      :width="280"
      :body-style="{ padding: '0' }"
      wrap-class-name="sidebar-drawer"
      @close="closeDrawer"
    >
      <Sidebar drawer-mode />
    </ADrawer>

    <!-- 右侧主区：顶栏 + 内容 + 底部 -->
    <div class="layout-main flex flex-col flex-1">
      <AppTopbar class="layout-topbar" />

      <main class="layout-content flex-1">
        <router-view
          v-slot="{ Component }"
          :key="route.path + isRefresh"
        >
          <transition name="slide-right" mode="out-in" appear>
            <div style="height: 100%; position: relative;">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </main>

      <footer v-if="showFooter" class="layout-footer">
        <AppFooter />
      </footer>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/layout' as *;
</style>

<style lang="scss">
/* 移动端抽屉里的侧边栏（drawer teleport 到 body，需非 scoped 才能命中） */
.sidebar-drawer {
  .ant-drawer-body {
    display: flex;
  }
  .sidebar {
    width: 100%;
    height: 100%;
    border-right: none;
  }
}
</style>

<script setup lang="ts">
/**
 * 顶栏组件
 * 左：移动端汉堡 + 当前页面标题；右：用户区（租户切换 + 头像）
 *
 * @author vaulka
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MenuOutlined } from '@ant-design/icons-vue'
import { useLayout } from '@/composables/useLayout'
import UserSection from './UserSection.vue'

const route = useRoute()
const { isMobile, openDrawer } = useLayout()

/** 当前页面标题 */
const pageTitle = computed(() => (route.meta?.title as string) || '')
</script>

<template>
  <header class="app-topbar flex items-center">
    <button
      v-if="isMobile"
      class="topbar-hamburger flex items-center justify-center"
      aria-label="打开菜单"
      @click="openDrawer"
    >
      <MenuOutlined />
    </button>
    <span class="topbar-title">{{ pageTitle }}</span>
    <div class="flex-1"></div>
    <!-- 桌面端账号与租户入口已收进新侧栏；移动端仍保留快捷入口 -->
    <UserSection v-if="isMobile" />
  </header>
</template>

<style scoped lang="scss">
@use '@/styles/components/topbar' as *;
</style>

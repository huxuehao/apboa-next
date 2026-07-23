<script setup lang="ts">
/**
 * 侧边栏导航组件
 * 竖排菜单（复用路由配置），桌面常驻可折叠 mini，移动端由布局包进抽屉
 *
 * @author vaulka
 */
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import type { Component } from 'vue'
import type { ItemType } from 'ant-design-vue'
import { bizRoutes } from '@/router/modules'
import { useLayout } from '@/composables/useLayout'
import AppLogo from './AppLogo.vue'
import {
  SafetyCertificateOutlined,
  FileTextOutlined,
  ApiOutlined,
  RobotOutlined,
  ToolOutlined,
  AppstoreOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  LoginOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  AccountBookOutlined,
} from '@ant-design/icons-vue'

interface Props {
  /** 是否折叠为 mini（只图标） */
  collapsed?: boolean
  /** 是否显示底部折叠按钮（抽屉模式下隐藏） */
  showCollapseBtn?: boolean
}
withDefaults(defineProps<Props>(), {
  collapsed: false,
  showCollapseBtn: true,
})

const route = useRoute()
const router = useRouter()
const { toggleCollapsed, closeDrawer } = useLayout()

/** 路由路径对应的图标映射 */
const iconMap: Record<string, Component> = {
  sensitive: SafetyCertificateOutlined,
  prompt: FileTextOutlined,
  model: ApiOutlined,
  agent: RobotOutlined,
  cost: AccountBookOutlined,
  workflow: ApartmentOutlined,
  tool: ToolOutlined,
  hook: LoginOutlined,
  skill: AppstoreOutlined,
  mcp: CloudServerOutlined,
  knowledge: DatabaseOutlined,
}

/** 从路由配置提取菜单项（供 a-menu inline 使用） */
const menuItems = computed<ItemType[]>(() => {
  const items: ItemType[] = []
  bizRoutes[0]?.children?.forEach((routeItem: RouteRecordRaw) => {
    if (routeItem.meta?.hidden !== true && routeItem.meta?.title) {
      const IconComponent = iconMap[routeItem.path] ?? AppstoreOutlined
      items.push({
        key: routeItem.path,
        icon: () => h(IconComponent),
        label: routeItem.meta?.title as string,
      })
    }
  })
  return items
})

/** 当前选中项（路由一级路径） */
const selectedKeys = computed(() => [route.path.split('/')[1]])

/** 点击菜单：跳转并（移动端）关抽屉 */
const onMenuClick = ({ key }: { key: string }) => {
  router.push('/' + key)
  closeDrawer()
}
</script>

<template>
  <aside class="app-sidebar flex flex-col" :class="{ collapsed }">
    <div class="sidebar-logo flex items-center">
      <AppLogo :collapsed="collapsed" />
    </div>

    <div class="sidebar-menu flex-1">
      <AMenu
        mode="inline"
        :inline-collapsed="collapsed"
        :selected-keys="selectedKeys"
        :items="menuItems"
        @click="onMenuClick"
      />
    </div>

    <div
      v-if="showCollapseBtn"
      class="sidebar-collapse-btn flex items-center"
      @click="toggleCollapsed"
    >
      <MenuUnfoldOutlined v-if="collapsed" />
      <template v-else>
        <MenuFoldOutlined />
        <span class="collapse-text">收起</span>
      </template>
    </div>
  </aside>
</template>

<style scoped lang="scss">
@use '@/styles/components/menu' as *;
</style>

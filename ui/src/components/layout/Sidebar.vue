<script setup lang="ts">
/**
 * 侧边栏组件
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAccountStore } from '@/stores'
import { RoutePaths } from '@/router/constants'
import SideMenu from './SideMenu.vue'
import {
  TeamOutlined,
  SwapOutlined,
  PoweroffOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ExportOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import SettingsModal from '@/components/settings/SettingsModal.vue'

const router = useRouter()
const {
  logout,
  userInfo,
  tenantRole,
  currentTenant,
  availableTenants
} = useAccountStore()

/** 系统设置模态窗显示状态 */
const settingsVisible = ref(false)

/** 打开设置弹窗时的目标菜单定位 */
const settingsTargetMenu = ref<string | undefined>(undefined)

/** 弹窗关闭时重置目标菜单 */
watch(settingsVisible, (val) => {
  if (!val) settingsTargetMenu.value = undefined
})

/** 侧边栏收缩状态 */
const collapsed = ref(false)

/** 切换收缩/展开 */
function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

/**
 * 用户名首字母(用于头像)
 */
const avatarText = computed(() => {
  if (!userInfo?.nickname) return '?'
  return userInfo.nickname.charAt(0).toUpperCase()
})

/**
 * 当前租户显示名称
 */
const currentTenantName = computed(() => {
  return currentTenant?.tenantName || currentTenant?.tenantCode || '未选择'
})

/**
 * 打开发现租户页面
 */
function openTenantDiscovery() {
  settingsTargetMenu.value = 'tenantDiscovery'
  settingsVisible.value = true
}

/**
 * 打开文档
 */
const openMarkdownDoc = () => {
  window.open('/doc.html#/', '_blank')
}

/**
 * 退出登录
 */
const handleLogout = () => {
  Modal.confirm({
    title: '确认',
    icon: null,
    content: '确认退出当前系统,是否继续?',
    onOk: async () => {
      await logout()
      await router.push(RoutePaths.LOGIN)
    }
  })
}

const roleName = computed(() => {
  if (!tenantRole) return '普通用户'
  switch (tenantRole) {
    case 'TENANT_OWNER':
      return '租户所有者'
    case 'TENANT_ADMIN':
      return '租户管理员'
    case 'TENANT_EDITOR':
      return '租户用户'
    default:
      return '普通用户'
  }
})
</script>

<template>
  <div class="sidebar flex flex-col" :class="{ collapsed: collapsed }">
    <!-- 顶部Logo区域 -->
    <div class="sidebar-logo">
      <img v-if="!collapsed" src="@/assets/logo/logo_3.png" alt="logo" width="130">
      <img v-else src="@/assets/logo/logo_1.png" alt="logo" width="38">
      <ATooltip v-if="!collapsed" :title="collapsed ? '展开菜单' : '收起菜单'" placement="right">
        <div class="collapse-btn" @click="toggleCollapsed">
          <MenuFoldOutlined v-if="!collapsed" />
          <MenuUnfoldOutlined v-else />
        </div>
      </ATooltip>
    </div>
    <div class="expand" v-if="collapsed">
      <ATooltip title="展开菜单" placement="right">
        <div class="expand-btn" @click="toggleCollapsed">
          <MenuUnfoldOutlined />
        </div>
      </ATooltip>
    </div>

    <!-- 租户切换区域 / 收缩后展开按钮 -->
    <div class="sidebar-tenant" v-if="!collapsed && availableTenants.length > 0">
      <div class="tenant-switcher" @click="openTenantDiscovery">
        <div class="tenant-info flex items-center gap-xs">
          <TeamOutlined />
          <span class="tenant-name">{{ currentTenantName }}</span>
        </div>
        <SwapOutlined class="tenant-icon" />
      </div>
    </div>
    <div class="expand" v-if="collapsed">
      <ATooltip title="切换租户" placement="right">
        <div class="expand-btn" @click="openTenantDiscovery">
          <SwapOutlined />
        </div>
      </ATooltip>
    </div>

    <!-- 菜单区域 -->
    <div class="sidebar-menu flex-1">
      <SideMenu :collapsed="collapsed" />
    </div>

    <!-- 定制菜单区域 -->
    <div class="sidebar-custom">
      <ATooltip :title="collapsed ? '文档' : ''" placement="right">
        <div class="custom-item" @click="openMarkdownDoc">
          <img src="@/assets/avatar/doc.png" width="20px" alt="icon"/>
          <span v-show="!collapsed">文档</span>
          <ExportOutlined v-show="!collapsed" class="doc-external-icon" />
        </div>
      </ATooltip>
      <ATooltip :title="collapsed ? '设置' : ''" placement="right">
        <div class="custom-item" @click="settingsVisible = true">
          <img src="@/assets/avatar/setting.png" width="20px" alt="icon"/>
          <span v-show="!collapsed">设置</span>
        </div>
      </ATooltip>
    </div>

    <!-- 底部用户区域 -->
    <div class="sidebar-user">
      <div class="user-info flex items-center gap-sm">
        <ATooltip :title="collapsed ? (userInfo?.username || '未登录') : ''" placement="right">
          <div class="user-avatar">
            {{ avatarText }}
          </div>
        </ATooltip>
        <div class="user-details" v-show="!collapsed">
          <div class="user-name">{{ userInfo?.nickname || '未登录' }}</div>
          <div class="user-role">{{ roleName }}</div>
        </div>
        <div class="user-actions" v-show="!collapsed">
          <PoweroffOutlined @click="handleLogout" class="logout-icon" />
        </div>
      </div>
    </div>

    <!-- 系统设置模态窗 -->
    <AModal
      v-model:open="settingsVisible"
      wrap-class-name="full-modal"
      :footer="null"
      :destroyOnClose="true"
      :width="'100%'"
    >
      <SettingsModal :defaultMenu="settingsTargetMenu" />
    </AModal>
  </div>
</template>

<style scoped lang="scss">
.sidebar {
  width: 280px;
  height: 100vh;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.3s ease;

  &.collapsed {
    width: 72px;
  }
}

.sidebar-logo {
  padding: 16px 20px 5px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .collapse-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 6px;
    cursor: pointer;
    color: #999;
    font-size: 16px;
    flex-shrink: 0;
    transition: all 0.2s ease;

    &:hover {
      background-color: #E9EAEA;
      color: #333;
    }
  }
}

.sidebar.collapsed .sidebar-logo {
  padding: 16px 8px 5px;
  justify-content: center;
  flex-direction: column;
  gap: 12px;

  .collapse-btn {
    margin-top: 4px;
  }
}


.project-name {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.sidebar-tenant {
  padding: 12px 16px;
}

.expand {
  display: flex;
  justify-content: center;
  padding: 8px 0 0 0;

  .expand-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 6px;
    cursor: pointer;
    color: #999;
    font-size: 17px;
    transition: all 0.2s ease;

    &:hover {
      background-color: #E9EAEA;
      color: #333;
    }
  }
}

.tenant-switcher {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  background-color: #ffffff;

  &:hover {
    border-color: #d8d8d8;
  }
}

.tenant-info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.tenant-name {
  font-size: 13px;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tenant-icon {
  font-size: 12px;
  color: #999;
  flex-shrink: 0;
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.sidebar-custom {
  padding: 0 16px;
}

.sidebar.collapsed .sidebar-custom {
  padding: 0 8px;

  .custom-item {
    justify-content: center;
    padding: 8px;
  }
}

.custom-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  color: #666;
  font-size: 14px;
  transition: all 0.2s ease;

  &:hover {
    background-color: #E9EAEA;
    color: #1a1a1a;
  }

  .doc-external-icon {
    margin-left: auto;
    font-size: 13px;
    color: #999;
    flex-shrink: 0;
  }
}

.sidebar-user {
  padding: 16px 20px;
}

.sidebar.collapsed .sidebar-user {
  padding: 16px 8px;

  .user-info {
    justify-content: center;
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #1677ff;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 500;
  flex-shrink: 0;
}

.user-details {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.user-actions {
  flex-shrink: 0;
}

.logout-icon {
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.2s ease;

  &:hover {
    color: #ff4d4f;
    background-color: #ffe3e2;
  }
}

/* 滚动条样式 */
.sidebar-menu {
  &::-webkit-scrollbar-track {
    background-color: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background-color: transparent;
  }

  &:hover::-webkit-scrollbar {
    width: 6px;
    height: 8px;
  }

  &:hover::-webkit-scrollbar-track {
    background-color: var(--color-bg-base);
    border-radius: var(--border-radius-base);
  }

  &:hover::-webkit-scrollbar-thumb {
    background-color: var(--color-border-base);
    border-radius: var(--border-radius-base);
    transition: background-color var(--transition-fast);
  }

  &:hover::-webkit-scrollbar-thumb:hover {
    background-color: var(--color-text-secondary);
  }
}
</style>

<script setup lang="ts">
/**
 * 用户信息区域组件 — 含租户切换器
 *
 * @author huxuehao
 */
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { RoutePaths } from '@/router/constants.ts'
import { useAccountStore } from '@/stores'
import {
  LogoutOutlined,
  SettingOutlined,
  BookOutlined,
  SwapOutlined,
  PlusOutlined,
  TeamOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import SettingsModal from '@/components/settings/SettingsModal.vue'
import { useLayout } from '@/composables/useLayout'
import type { TenantInfo } from '@/types'

const router = useRouter()
const { isMobile } = useLayout()
const {
  logout,
  userInfo,
  currentTenant,
  availableTenants,
  switchTenant
} = useAccountStore()

/** 系统设置模态窗显示状态 */
const settingsVisible = ref(false)

/** 租户切换加载中 */
const switchingTenant = ref(false)

/**
 * 用户名首字母(用于头像)
 */
const avatarText = computed(() => {
  if (!userInfo?.username) return '?'
  return userInfo.username.charAt(0).toUpperCase()
})

/**
 * 当前租户显示名称
 */
const currentTenantName = computed(() => {
  return currentTenant?.tenantName || currentTenant?.tenantCode || '未选择'
})

/**
 * 下拉菜单项
 */
const menuItems = [
  {
    key: 'doc',
    label: '文档',
    icon: () => h(BookOutlined),
  },
  {
    type: 'divider',
  },
  {
    key: 'profile',
    label: '设置',
    icon: () => h(SettingOutlined),
  },
  {
    type: 'divider',
  },
  {
    key: 'logout',
    danger: true,
    label: '退出',
    icon: () => h(LogoutOutlined),
  }
]

/**
 * 处理菜单点击
 */
const handleMenuClick = async ({ key }: { key: string }) => {
  if (key === 'logout') {
    Modal.confirm({
      title: '确认',
      icon: null,
      content: '确认退出当前系统,是否继续?',
      onOk: async () => {
        await logout()
        await router.push(RoutePaths.LOGIN)
      }
    })
  } else if (key === 'profile') {
    settingsVisible.value = true
  } else if (key === 'doc') {
    openMarkdownDoc()
  }
}

/**
 * 切换租户
 */
async function handleSwitchTenant(tenant: TenantInfo) {
  if (tenant.tenantId === currentTenant?.tenantId) return
  Modal.confirm({
    title: '切换工作空间',
    icon: null,
    content: `确认切换到「${tenant.tenantName}」？切换后当前页面将刷新。`,
    onOk: async () => {
      try {
        switchingTenant.value = true
        await switchTenant({ tenantId: tenant.tenantId })
        message.success('已切换工作空间')
        location.reload()
      } catch {
        message.error('切换失败，请重试')
      } finally {
        switchingTenant.value = false
      }
    }
  })
}

/**
 * 打开发现租户页面
 */
function openTenantDiscovery() {
  settingsVisible.value = true
}

const openMarkdownDoc = () => {
  window.open('/doc.html#/', '_blank')
}
</script>

<template>
  <div class="user-section flex items-center gap-sm pr-md">
    <!-- 租户切换器 -->
    <ADropdown :trigger="['click']" v-if="availableTenants.length > 0">
      <div class="tenant-switcher flex items-center gap-xs cursor-pointer">
        <TeamOutlined />
        <span class="tenant-name">{{ currentTenantName }}</span>
        <SwapOutlined class="tenant-icon" />
      </div>
      <template #overlay>
        <AMenu>
          <AMenuItem
            v-for="tenant in availableTenants"
            :key="tenant.tenantId"
            @click="handleSwitchTenant(tenant)"
          >
            <span class="flex justify-between items-center" style="gap: 24px;">
              <span>{{ tenant.tenantName }}</span>
              <ATag
                v-if="tenant.tenantId === currentTenant?.tenantId"
                color="blue"
                size="small"
                class="ml-sm"
                :bordered="false"
              >
                当前
              </ATag>
            </span>
          </AMenuItem>
          <AMenuDivider />
          <AMenuItem key="discover" @click="openTenantDiscovery">
            <PlusOutlined />
            <span class="ml-sm">发现更多组织</span>
          </AMenuItem>
        </AMenu>
      </template>
    </ADropdown>

    <ADropdown :trigger="['hover']">
      <div class="user-info flex items-center gap-sm">
        <AAvatar :size="32" style="background-color: var(--color-primary)">
          {{ avatarText }}
        </AAvatar>
      </div>

      <template #overlay>
        <AMenu :items="menuItems" @click="handleMenuClick" />
      </template>
    </ADropdown>

    <!-- 系统设置模态窗 -->
    <AModal
      v-model:open="settingsVisible"
      wrap-class-name="full-modal"
      :footer="null"
      :destroyOnClose="true"
      :width="'100%'"
      :closable="!isMobile"
    >
      <SettingsModal @close="settingsVisible = false" />
    </AModal>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/components/user-section' as *;

.tenant-switcher {
  padding: 4px 12px;
  border: 1px solid var(--border-color, #d9d9d9);
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-primary, #1f1f1f);
  transition: all 0.2s ease;
  max-width: 140px;

  &:hover {
    border-color: var(--primary-color, #1677ff);
    background: var(--primary-bg-hover, #f0f5ff);
  }
}

.tenant-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 130px;
  margin: 0 4px;
}

.tenant-icon {
  font-size: 12px;
  color: var(--text-tertiary, #999);
  flex-shrink: 0;
}
</style>

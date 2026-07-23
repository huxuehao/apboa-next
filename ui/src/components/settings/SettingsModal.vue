/**
 * 系统设置主模态窗组件
 * 桌面：左菜单常驻两栏；移动端(<1024)：菜单收进左侧抽屉，顶栏 X 关闭 Modal + 汉堡开菜单
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, provide, computed } from 'vue'
import { MenuOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { useLayout } from '@/composables/useLayout'
import SettingsMenu from './SettingsMenu.vue'
import MyAccount from './MyAccount.vue'
import AllAccounts from './AllAccounts.vue'
import ApiKeys from './ApiKeys.vue'
import StorageManagement from './StorageManagement.vue'
import SystemParams from './SystemParams.vue'
import SystemIntro from './SystemIntro.vue'
import TenantSettings from './TenantSettings.vue'
import TenantDiscovery from './TenantDiscovery.vue'
import ExecutionNodes from './ExecutionNodes.vue'

const props = defineProps<{
  /** 打开时默认定位的菜单项 */
  defaultMenu?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { isMobile } = useLayout()
/** 当前选中的菜单项 */
const currentMenu = ref<string>(props.defaultMenu || 'myAccount')

/** 移动端菜单抽屉开合 */
const menuOpen = ref(false)

/** 提供给子组件的上下文 */
provide('currentMenu', currentMenu)

/** 设置项 key → 标题（移动端顶栏显示当前项名） */
const titleMap: Record<string, string> = {
  myAccount: '我的账户',
  tenantSettings: '组织管理',
  tenantDiscovery: '发现组织',
  apiKeys: 'API Keys',
  storageManagement: '存储管理',
  systemParams: '系统参数',
  executionNodes: '服务监控',
  systemIntro: '系统介绍',
  allAccounts: '全部账户',
}
const currentTitle = computed(() => titleMap[currentMenu.value] || '系统设置')

/** 切换菜单项（移动端选中后收起抽屉） */
function onMenuChange(key: string) {
  currentMenu.value = key
  menuOpen.value = false
}

defineExpose({
  currentMenu
})
</script>

<template>
  <div class="settings-container" :class="{ 'is-mobile': isMobile }">
    <!-- 移动端顶栏：X 关闭 Modal + 汉堡开菜单 + 当前项标题 -->
    <div v-if="isMobile" class="settings-mobile-topbar">
      <button class="settings-topbar-btn settings-close-btn" aria-label="关闭设置" @click="emit('close')">
        <CloseOutlined />
      </button>
      <button class="settings-topbar-btn" aria-label="设置菜单" @click="menuOpen = true">
        <MenuOutlined />
      </button>
      <span class="settings-topbar-title">{{ currentTitle }}</span>
    </div>

    <!-- 移动端菜单抽屉遮罩 -->
    <div v-if="isMobile && menuOpen" class="settings-menu-mask" @click="menuOpen = false"></div>

    <!-- 设置菜单：桌面常驻 / 移动抽屉 -->
    <div class="settings-sidebar" :class="{ open: menuOpen }">
      <SettingsMenu :model-value="currentMenu" @update:model-value="onMenuChange" />
    </div>
    <div class="settings-divider"></div>
    <div class="settings-content">
      <MyAccount v-if="currentMenu === 'myAccount'" />
      <AllAccounts v-else-if="currentMenu === 'allAccounts'" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']" />
      <ApiKeys v-else-if="currentMenu === 'apiKeys'" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']" />
      <StorageManagement v-else-if="currentMenu === 'storageManagement'" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']" />
      <SystemParams v-else-if="currentMenu === 'systemParams'" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']" />
      <TenantSettings v-else-if="currentMenu === 'tenantSettings'" />
      <TenantDiscovery v-else-if="currentMenu === 'tenantDiscovery'" />
      <ExecutionNodes v-else-if="currentMenu === 'executionNodes'" />
      <SystemIntro v-else-if="currentMenu === 'systemIntro'" />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/_settings.scss' as *;
</style>

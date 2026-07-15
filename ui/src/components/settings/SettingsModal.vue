/**
 * 系统设置主模态窗组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, provide } from 'vue'
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

/**
 * Props定义
 */
const props = defineProps<{
  /** 打开时默认定位的菜单项 */
  defaultMenu?: string
}>()

/** 当前选中的菜单项 */
const currentMenu = ref<string>(props.defaultMenu || 'myAccount')

/** 提供给子组件的上下文 */
provide('currentMenu', currentMenu)

defineExpose({
  currentMenu
})
</script>

<template>
  <div class="settings-container">
    <div class="settings-sidebar">
      <SettingsMenu v-model="currentMenu" />
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

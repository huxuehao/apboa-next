<script setup lang="ts">
/**
 * 应用Logo组件
 *
 * @author huxuehao
 */
interface Props {
  projectName?: string
  /** 折叠态：只显示图标，隐藏项目名 */
  collapsed?: boolean
}

withDefaults(defineProps<Props>(), {
  projectName: 'APBOA-NEXT',
  collapsed: false,
})
</script>

<template>
  <div class="app-logo flex items-center gap-sm cursor-pointer" :class="{ 'is-collapsed': collapsed }">
    <div class="logo-icon flex items-center justify-center">
      <img src="@/assets/images/logo/logo.png" alt="logo" width="32">
    </div>
    <span v-show="!collapsed" class="project-name">{{ projectName }}</span>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/components/logo' as *;
</style>

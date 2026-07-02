<script setup lang="ts">
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'

withDefaults(defineProps<{
  icon?: IconName
  iconColor?: string
  iconSize?: number
}>(), {
  iconSize: 12,
})
</script>

<template>
  <div class="summary-row">
    <IconFont v-if="icon" :name="icon" :size="iconSize" :color="iconColor" />
    <span class="summary-content"><slot /></span>
  </div>
</template>

<style scoped lang="scss">
.summary-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  background: #F5F6F8;
  font-size: 12px;
  line-height: 20px;
  color: #595959;
  overflow: hidden;
}

.summary-row :deep(.iconfont) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.summary-content {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;

  :deep(code) {
    display: inline-block;
    padding: 0 4px;
    border-radius: 3px;
    background: #e8e8e8;
    font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
    font-size: 11px;
    color: #595959;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    line-height: 18px;
  }

  :deep(span) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>

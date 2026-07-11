<script setup lang="ts">
defineProps<{
  title: string
  collapsed?: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <div class="panel-section">
    <div class="section-header" :class="{ collapsed: collapsed }" @click="$emit('toggle')">
      <span class="section-title">{{ title }}</span>
      <span v-if="$slots.actions" class="section-actions">
        <slot name="actions" />
      </span>
    </div>
    <div v-show="!collapsed" class="section-body">
      <slot />
    </div>
  </div>
</template>

<style scoped lang="scss">
.panel-section {
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
}

.section-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 0 0 6px 10px;
  color: #2D2626;
  font-size: 15px;
  font-weight: 700;
  user-select: none;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 2px;
    bottom: 10px;
    width: 3px;
    border-radius: 2px;
    background: #2D2626;
  }

  &.collapsed {
    margin-bottom: 0;
  }
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-left: 4px
}

.section-body {
  padding-left: 2px;
}
</style>

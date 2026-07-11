<script setup lang="ts">
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

defineEmits<{
  click: []
}>()
</script>

<template>
  <span class="guide-trigger" @click="$emit('click')">
    <QuestionCircleOutlined />
  </span>
</template>

<style scoped lang="scss">
.guide-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  color: #8c8c8c;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #f0f0f0;
    color: #1677ff;
  }
}
</style>

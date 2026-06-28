<script setup lang="ts">
import {
  BugOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  HistoryOutlined,
  PlayCircleOutlined,
  SaveOutlined,
} from '@ant-design/icons-vue'

defineProps<{
  saving?: boolean
  running?: boolean
}>()

defineEmits<{
  save: []
  validate: []
  publish: []
  debug: []
  versions: []
  runs: []
}>()
</script>

<template>
  <div class="top-actions">
    <ATooltip title="保存草稿">
      <AButton :loading="saving" @click="$emit('save')">
        <template #icon><SaveOutlined /></template>
        保存
      </AButton>
    </ATooltip>
    <ATooltip title="校验当前草稿">
      <AButton @click="$emit('validate')">
        <template #icon><CheckCircleOutlined /></template>
        校验
      </AButton>
    </ATooltip>
    <ATooltip title="打开底部调试面板">
      <AButton :loading="running" @click="$emit('debug')">
        <template #icon><BugOutlined /></template>
        调试
      </AButton>
    </ATooltip>
    <ATooltip title="发布为不可变版本">
      <AButton @click="$emit('publish')">
        <template #icon><CloudUploadOutlined /></template>
        发布
      </AButton>
    </ATooltip>
    <ATooltip title="版本记录">
      <AButton @click="$emit('versions')">
        <template #icon><HistoryOutlined /></template>
        版本
      </AButton>
    </ATooltip>
    <ATooltip title="运行记录">
      <AButton @click="$emit('runs')">
        <template #icon><PlayCircleOutlined /></template>
        运行
      </AButton>
    </ATooltip>
  </div>
</template>

<style scoped lang="scss">
.top-actions {
  position: absolute;
  right: 16px;
  top: 16px;
  z-index: 15;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  max-width: calc(100vw - 470px);
}

:deep(.ant-btn) {
  border: 1px solid transparent;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
}

:deep(.ant-btn:hover),
:deep(.ant-btn:focus) {
  color: #000000;
  border-color: #d8d8d8;
}
</style>

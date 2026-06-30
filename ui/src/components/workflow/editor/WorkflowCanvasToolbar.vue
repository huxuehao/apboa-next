<script setup lang="ts">
import {
  AimOutlined,
  ClearOutlined,
  CompressOutlined,
  LockOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  NodeIndexOutlined,
  PlusCircleOutlined,
  RedoOutlined,
  UndoOutlined,
  UnlockOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue';

const props = defineProps<{
  locked: boolean
  readonly: boolean
  canUndo: boolean
  canRedo: boolean
  hasNodes: boolean
  libraryOpen: boolean
  lockToggling?: boolean
}>()

const emit =defineEmits<{
  addNode: []
  fit: []
  zoomIn: []
  zoomOut: []
  resetZoom: []
  toggleLock: []
  undo: []
  redo: []
  layout: []
  clearSelection: []
}>()

const handleToggleLock = () => {
  if (props.locked) {
    Modal.confirm({
      title: '解锁工作流',
      content: '解锁工作流后将进入只读模式，是否确认？',
      onOk: () => {
        emit('toggleLock')
      }
    })
  } else {
    emit('toggleLock')
  }
}
</script>

<template>
  <div class="canvas-toolbar" aria-label="画布快捷工具栏">
    <ATooltip placement="right" title="添加节点">
      <AButton :disabled="readonly" :type="libraryOpen ? 'primary' : 'text'" @click="$emit('addNode')">
        <template #icon><PlusCircleOutlined /></template>
      </AButton>
    </ATooltip>
    <div class="toolbar-divider" />
    <ATooltip placement="right" title="适配全部节点">
      <AButton type="text" :disabled="!hasNodes || readonly" @click="$emit('fit')">
        <template #icon><AimOutlined /></template>
      </AButton>
    </ATooltip>
    <ATooltip placement="right" title="放大">
      <AButton type="text" @click="$emit('zoomIn')">
        <template #icon><ZoomInOutlined /></template>
      </AButton>
    </ATooltip>
    <ATooltip placement="right" title="缩小">
      <AButton type="text" @click="$emit('zoomOut')">
        <template #icon><ZoomOutOutlined /></template>
      </AButton>
    </ATooltip>
    <ATooltip placement="right" title="重置缩放">
      <AButton type="text" @click="$emit('resetZoom')">
        <template #icon><CompressOutlined /></template>
      </AButton>
    </ATooltip>
    <div class="toolbar-divider" />
    <ATooltip placement="right" :title="locked ? '锁定工作流' : '解锁工作流'">
      <AButton :disabled="readonly" :type="locked ? 'primary' : 'text'" :loading="lockToggling" @click="handleToggleLock">
        <template #icon>
          <LockOutlined v-if="locked" />
          <UnlockOutlined v-else />
        </template>
      </AButton>
    </ATooltip>
    <ATooltip placement="right" title="撤销">
      <AButton type="text" :disabled="!canUndo || readonly" @click="$emit('undo')">
        <template #icon><UndoOutlined /></template>
      </AButton>
    </ATooltip>
    <ATooltip placement="right" title="重做">
      <AButton type="text" :disabled="!canRedo || readonly" @click="$emit('redo')">
        <template #icon><RedoOutlined /></template>
      </AButton>
    </ATooltip>
    <ATooltip placement="right" title="整理布局">
      <AButton type="text" :disabled="!hasNodes || readonly" @click="$emit('layout')">
        <template #icon><NodeIndexOutlined /></template>
      </AButton>
    </ATooltip>
    <ATooltip placement="right" title="清空选择">
      <AButton :disabled="readonly" type="text" @click="$emit('clearSelection')">
        <template #icon><ClearOutlined /></template>
      </AButton>
    </ATooltip>
  </div>
</template>

<style scoped lang="scss">
.canvas-toolbar {
  position: absolute;
  left: 16px;
  top: calc(50% - 55px);
  z-index: 15;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: calc(100vh - 160px);
  padding: 6px;
  overflow: auto;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
  transform: translateY(-50%);
}

.canvas-toolbar :deep(.ant-btn) {
  width: 34px;
  height: 34px;
  padding: 0;
}

.toolbar-divider {
  height: 1px;
  margin: 3px 4px;
  background: #f0f0f0;
}

:deep(.ant-btn-primary) {
  background-color: #F0F0F0;
  color: #000000;
  box-shadow: none;
}

:deep(.ant-btn-primary:hover),
:deep(.ant-btn-primary:focus) {
  background-color: #E0E0E0;
  color: #000000;
  box-shadow: none;
}
</style>

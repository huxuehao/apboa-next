<script setup lang="ts">
import {
  AimOutlined,
  ClearOutlined,
  CompressOutlined,
  LockOutlined,
  NodeIndexOutlined,
  PlusCircleOutlined,
  RedoOutlined,
  UndoOutlined,
  UnlockOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue';
import { GuideModal, guideEntries } from '../guide'
import WorkflowVariableButton from './WorkflowVariableButton.vue'

const props = defineProps<{
  locked: boolean
  canUndo: boolean
  canRedo: boolean
  hasNodes: boolean
  libraryOpen: boolean
  lockToggling?: boolean
  hideLock?: boolean
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
  <div class="canvas-left-group" aria-label="画布左侧工具栏">
    <!-- 变量按钮 -->
    <WorkflowVariableButton />

    <!-- 快捷工具栏 -->
    <div class="canvas-toolbar" aria-label="画布快捷工具栏">
      <ATooltip placement="right" title="添加节点">
        <AButton :type="libraryOpen ? 'primary' : 'text'" @click="$emit('addNode')">
          <template #icon><PlusCircleOutlined /></template>
        </AButton>
      </ATooltip>
      <div class="toolbar-divider" />
      <ATooltip placement="right" title="适配全部节点">
        <AButton type="text" :disabled="!hasNodes" @click="$emit('fit')">
          <template #icon><AimOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip placement="right" title="重置缩放">
        <AButton type="text" @click="$emit('resetZoom')">
          <template #icon><CompressOutlined /></template>
        </AButton>
      </ATooltip>
      <div class="toolbar-divider" />
      <ATooltip v-if="!hideLock" placement="right" :title="locked ? '锁定工作流' : '解锁工作流'">
        <AButton :type="locked ? 'primary' : 'text'" :loading="lockToggling" @click="handleToggleLock">
          <template #icon>
            <LockOutlined v-if="locked" />
            <UnlockOutlined v-else />
          </template>
        </AButton>
      </ATooltip>
      <ATooltip placement="right" title="撤销">
        <AButton type="text" :disabled="!canUndo" @click="$emit('undo')">
          <template #icon><UndoOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip placement="right" title="重做">
        <AButton type="text" :disabled="!canRedo" @click="$emit('redo')">
          <template #icon><RedoOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip placement="right" title="整理布局">
        <AButton type="text" :disabled="!hasNodes" @click="$emit('layout')">
          <template #icon><NodeIndexOutlined /></template>
        </AButton>
      </ATooltip>
      <ATooltip placement="right" title="清空选择">
        <AButton type="text" @click="$emit('clearSelection')">
          <template #icon><ClearOutlined /></template>
        </AButton>
      </ATooltip>
      <div class="toolbar-divider" />
      <ATooltip placement="right" title="使用说明">
        <GuideModal :entries="guideEntries" />
      </ATooltip>
    </div>
  </div>
</template>

<style scoped lang="scss">
// ========== 左侧工具栏组（变量按钮 + 快捷工具栏） ==========
.canvas-left-group {
  position: absolute;
  left: 16px;
  top: calc(50% - 28px);
  z-index: 15;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  transform: translateY(-50%);
}

.canvas-toolbar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: calc(100vh - 200px);
  padding: 6px;
  overflow: auto;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
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

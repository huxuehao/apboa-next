/**
 * 工作流项节点组件
 * 展示Agent绑定的工作流及当前可用状态
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { BranchesOutlined } from '@ant-design/icons-vue'
import type { WorkflowItemNodeData } from '../types'

const props = defineProps<{
  data: WorkflowItemNodeData
}>()

const descriptionText = computed(() => {
  const description = props.data.workflow.remark || '暂无描述'
  return description.length > 40 ? `${description.slice(0, 40)}...` : description
})

const state = computed(() => {
  const workflow = props.data.workflow
  if (workflow.loadFailed) return { text: '读取失败', color: 'red' }
  if (workflow.available) return { text: '当前可用', color: 'green' }
  if (workflow.enabled === false) return { text: '已禁用', color: 'default' }
  if (workflow.status === 'DRAFT') return { text: '未发布', color: 'orange' }
  return { text: '当前不可用', color: 'default' }
})
</script>

<template>
  <div class="workflow-item-node" :class="{ unavailable: !data.workflow.available }">
    <Handle type="target" :position="Position.Top" id="top" />
    <Handle type="target" :position="Position.Right" id="right" />
    <Handle type="target" :position="Position.Bottom" id="bottom" />
    <Handle type="target" :position="Position.Left" id="left" />

    <div class="node-header">
      <div class="node-avatar">
        <BranchesOutlined />
      </div>
      <div class="node-name" :title="data.workflow.name">
        {{ data.workflow.name }}
      </div>
    </div>

    <div class="node-desc" :title="data.workflow.remark">
      {{ descriptionText }}
    </div>

    <div class="node-footer">
      <ATag :bordered="false" size="small" color="orange">Workflow</ATag>
      <ATag :bordered="false" size="small" :color="state.color">{{ state.text }}</ATag>
    </div>
  </div>
</template>

<style scoped lang="scss">
.workflow-item-node {
  width: 220px;
  padding: 12px;
  border: 1px solid #ffe7ba;
  border-radius: 10px;
  background: white;
  box-shadow: 0 2px 8px rgba(250, 140, 22, 0.08);
  transition: all 0.2s ease;

  &:hover {
    box-shadow: 0 4px 12px rgba(250, 140, 22, 0.3);
  }

  &.unavailable {
    border-color: #d9d9d9;
    opacity: 0.68;

    .node-avatar {
      background: #f5f5f5;
      color: #8c8c8c;
    }
  }

  .node-header {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 8px;
  }

  .node-avatar {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: #fff7e6;
    color: #fa8c16;
    font-size: 16px;
  }

  .node-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    color: #262626;
    font-size: 13px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .node-desc {
    display: -webkit-box;
    min-height: 33px;
    margin-bottom: 8px;
    overflow: hidden;
    color: #8c8c8c;
    font-size: 11px;
    line-height: 1.5;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .node-footer {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;

    :deep(.ant-tag) {
      margin: 0;
      padding: 0 6px;
      font-size: 10px;
      line-height: 18px;
    }
  }

  :deep(.vue-flow__handle) {
    width: 8px;
    height: 8px;
    border: none;
    background: transparent;
    opacity: 0;
  }
}
</style>

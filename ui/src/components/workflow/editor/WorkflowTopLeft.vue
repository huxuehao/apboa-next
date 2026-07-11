<script setup lang="ts">
import { ArrowLeftOutlined, LoadingOutlined } from '@ant-design/icons-vue'

defineProps<{
  title: string
  status?: string
  version?: string
  saving?: boolean
  saveState?: 'idle' | 'dirty' | 'saved'
  readonly?: boolean
}>()

defineEmits<{
  back: []
  updateTitle: [value: string]
}>()
</script>

<template>
  <div class="top-left">
    <AButton type="text" class="back-btn" @click="$emit('back')">
      <template #icon><ArrowLeftOutlined /></template>
    </AButton>

    <div class="title-row">
      <div class="title-input-wrap">
        <AInput
          class="title-input"
          :value="title"
          :disabled="readonly"
          placeholder="未命名工作流"
          @update:value="(value: string) => $emit('updateTitle', value)"
        />
        <span class="title-sizer" aria-hidden="true">{{ title || '未命名工作流' }}</span>
      </div>
      <span class="title-meta">
        <span>{{ status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
        <span>v{{ version || '0' }}</span>
        <span v-if="saving" class="save-state save-state-saving">
          <LoadingOutlined />
          保存中...
        </span>
        <span v-else-if="saveState === 'dirty'" class="save-state save-state-dirty">未保存</span>
        <span v-else-if="saveState === 'saved'" class="save-state save-state-saved">已保存</span>
        <span v-else class="save-state save-state-idle">无变化</span>
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.top-left {
  position: absolute;
  left: 16px;
  top: 16px;
  z-index: 15;
  display: grid;
  grid-template-columns: 36px auto;
  align-items: start;
}

.back-btn {
  width: 34px;
  height: 34px;
  padding: 0;
}

.title-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  margin-top: 4px;
}

:deep(.ant-input) {
  background: transparent !important;
  width: 100% !important;
  min-width: 0 !important;
}

.title-input-wrap {
  position: relative;
  display: inline-block;
  min-width: 50px;
  max-width: 100%;
}

.title-input {
  position: absolute;
  inset: 0;
  width: 100%;
  padding: 0px 8px;
  border: 0;
  color: #262626;
  font-size: 16px;
  font-weight: 700;
}

.title-sizer {
  visibility: hidden;
  white-space: pre;
  padding: 0px 8px;
  font-size: 16px;
  font-weight: 700;
}

.title-input:focus {
  box-shadow: none;
}

.title-meta {
  display: flex;
  gap: 10px;
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
}

.save-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.save-state-saving {
  color: #1677ff;
}

.save-state-dirty {
  color: #faad14;
}

.save-state-saved {
  color: #52c41a;
}

.save-state-idle {
  color: #8c8c8c;
}
</style>

<script setup lang="ts">
/**
 * 聊天输入框附件列表组件
 * 负责展示已上传/上传中的附件，并支持移除与预览
 *
 * @component
 */
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { CloseOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import MediaPreview from '@/components/common/MediaPreview.vue'
import MediaIcon from '@/components/common/MediaIcon.vue'
import AttachImage from '@/components/common/AttachImage.vue'
import { getExtension } from '@/composables/chat/useChatAttachments'
import { isImageExtension } from '@/utils/chat/attachImage'
import type { UploadedFileItem } from '@/types'

defineProps<{
  files: UploadedFileItem[]
}>()

const emit = defineEmits<{
  (e: 'remove', item: UploadedFileItem): void
}>()

/** 预览弹层显隐 */
const previewVisible = ref(false)
/** 当前预览索引 */
const previewCurrentIndex = ref(0)

/**
 * 打开附件预览，上传中的附件提示并跳过
 *
 * @param item 附件项
 * @param index 当前索引
 */
const openPreview = (item: UploadedFileItem, index: number) => {
  if (item.uploading) {
    message.warning('文件上传中')
    return
  }
  previewCurrentIndex.value = index
  previewVisible.value = true
}

/**
 * 移除附件
 *
 * @param item 附件项
 */
const handleRemove = (item: UploadedFileItem) => {
  emit('remove', item)
}
</script>

<template>
  <template v-if="files.length > 0">
    <div class="chat-input-files-row">
      <div class="chat-input-files-scroll">
        <template v-for="(item, index) in files" :key="item.id">
          <!-- 图片：保比例预览卡 -->
          <div
            v-if="isImageExtension(item.extension || getExtension(item.name))"
            class="chat-input-image-item"
            @click="openPreview(item, index)"
          >
            <AttachImage
              class="chat-input-image"
              :attach-id="item.id"
              :name="item.name"
              :extension="item.extension || getExtension(item.name)"
              :local-url="item.localUrl"
            />
            <span v-if="item.uploading" class="chat-input-image-mask">
              <LoadingOutlined spin />
            </span>
            <button
              type="button"
              class="chat-input-image-remove"
              title="移除"
              @click.stop="handleRemove(item)"
            >
              <CloseOutlined />
            </button>
          </div>
          <!-- 其他类型：保持原有文件名 chip -->
          <div
            v-else
            @click="openPreview(item, index)"
            class="chat-input-file-item"
          >
            <span v-if="item.uploading" class="chat-input-file-loading">
              <LoadingOutlined spin />
            </span>
            <MediaIcon :type="(item.extension ?? getExtension(item.name)) || 'FILE'" size="19" />
            <span class="chat-input-file-name" :title="item.name">{{ item.name }}</span>
            <button
              type="button"
              class="chat-input-file-remove"
              title="移除"
              @click.stop="handleRemove(item)"
            >
              <CloseOutlined />
            </button>
          </div>
        </template>
      </div>
    </div>

    <MediaPreview
      v-if="previewVisible"
      v-model:visible="previewVisible"
      :items="files"
      :current-index="previewCurrentIndex"
    />
  </template>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

.chat-input-files-row {
  flex-shrink: 0;
  min-height: 0;
  overflow: hidden;
}

.chat-input-files-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 4px 0;
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-light) transparent;

  &::-webkit-scrollbar {
    height: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--color-border-light);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: var(--color-text-placeholder);
  }
}

.chat-input-file-item {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 280px;
  padding: 6px 10px;
  background: #F5F6F8;
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
  transition: background-color 0.2s ease, border-color 0.2s ease;
  cursor: pointer;

  &:hover {
    background: rgba($chat-primary, 0.1);
  }
}

/* 图片附件：保比例预览卡（约束范围，非小方块缩略图） */
.chat-input-image-item {
  position: relative;
  flex-shrink: 0;
  cursor: pointer;
  border-radius: var(--border-radius-md);

  :deep(.attach-image),
  :deep(.attach-image-skeleton) {
    max-width: 200px;
    max-height: 120px;
    border: 1px solid var(--color-border-extra-light);
  }

  :deep(.attach-image-skeleton) {
    width: 120px;
    height: 90px;
  }
}

.chat-input-image-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.55);
  border-radius: var(--border-radius-md);
  color: $chat-primary;
  font-size: 18px;
  pointer-events: none;
}

.chat-input-image-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  font-size: 10px;
  transition: background-color 0.2s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.75);
  }
}

.chat-input-file-loading {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  color: $chat-primary;
  font-size: 14px;
}

.chat-input-file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-primary);
}

.chat-input-file-remove {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-text-placeholder);
  cursor: pointer;
  border-radius: 50%;
  font-size: 12px;
  transition: color 0.2s ease, background-color 0.2s ease;

  &:hover {
    color: var(--color-text-primary);
    background: rgba(0, 0, 0, 0.08);
  }
}
</style>

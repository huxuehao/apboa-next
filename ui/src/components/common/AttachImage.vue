/**
 * 附件图片预览组件
 *
 * 保持原始宽高比渲染附件图片，最大尺寸由使用方样式约束。
 * 取图优先级：本地即时预览 localUrl > 模块缓存 > 鉴权接口拉取 blob；
 * 拉取失败回退为「图标 + 文件名」chip，不阻断消息展示。
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import MediaIcon from './MediaIcon.vue'
import { download } from '@/api/attach'
import { getCachedAttachUrl, setCachedAttachUrl } from '@/utils/chat/attachImage'

const props = defineProps<{
  /** 附件 ID（temp- 前缀表示上传中，尚无法从后端拉取） */
  attachId: string
  name: string
  extension?: string
  /** 本地即时预览地址（选图后立即可用），优先于网络拉取 */
  localUrl?: string
}>()

const url = ref<string | null>(null)
const failed = ref(false)

async function load() {
  failed.value = false
  if (props.localUrl) {
    url.value = props.localUrl
    return
  }
  const cached = getCachedAttachUrl(props.attachId)
  if (cached) {
    url.value = cached
    return
  }
  if (props.attachId.startsWith('temp-')) {
    // 上传中且无本地预览：显示骨架等 id 就绪
    url.value = null
    return
  }
  try {
    const res = await download(props.attachId)
    const blobUrl = URL.createObjectURL(new Blob([res.data]))
    setCachedAttachUrl(props.attachId, blobUrl)
    url.value = blobUrl
  } catch {
    failed.value = true
  }
}

watch(() => [props.attachId, props.localUrl], () => {
  void load()
}, { immediate: true })
</script>

<template>
  <span v-if="failed" class="attach-image-fallback">
    <MediaIcon :type="extension || 'FILE'" size="19" />
    <span class="attach-image-fallback-name" :title="name">{{ name }}</span>
  </span>
  <span v-else-if="!url" class="attach-image-skeleton">
    <LoadingOutlined spin />
  </span>
  <img
    v-else
    :src="url"
    :alt="name"
    :title="name"
    class="attach-image"
    draggable="false"
  />
</template>

<style scoped lang="scss">
.attach-image {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  border-radius: var(--border-radius-md);
  object-fit: contain;
}

.attach-image-skeleton {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 160px;
  height: 120px;
  max-width: 100%;
  border-radius: var(--border-radius-md);
  background-color: var(--color-bg-light);
  color: var(--color-text-placeholder);
  font-size: 18px;
}

.attach-image-fallback {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 280px;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);

  .attach-image-fallback-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--color-text-primary);
  }
}
</style>

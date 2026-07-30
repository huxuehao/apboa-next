<script setup lang="ts">
/**
 * 图片/视频面板：按地址展示图片或视频，无数据集依赖。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  data?: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const mediaType = computed(() => (props.panel.options?.mediaType as string) || 'image')
const url = computed(() => (props.panel.options?.url as string) || '')
const fit = computed<'contain' | 'cover' | 'fill'>(
  () => ((props.panel.options?.fit as string) || 'contain') as 'contain' | 'cover' | 'fill',
)
const autoplay = computed(() => props.panel.options?.autoplay === true)
const loop = computed(() => props.panel.options?.loop === true)
const muted = computed(() => props.panel.options?.muted !== false)
</script>

<template>
  <div class="media-panel">
    <span v-if="!url" class="media-empty">在配置面板填写图片/视频地址</span>
    <img v-else-if="mediaType === 'image'" class="media-el" :src="url" :style="{ objectFit: fit }" />
    <video
      v-else
      class="media-el"
      :src="url"
      :autoplay="autoplay"
      :loop="loop"
      :muted="muted"
      controls
      :style="{ objectFit: fit }"
    />
  </div>
</template>

<style scoped lang="scss">
.media-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  overflow: hidden;
}

.media-el {
  width: 100%;
  height: 100%;
}

.media-empty {
  font-size: 13px;
  color: #bbb;
}
</style>

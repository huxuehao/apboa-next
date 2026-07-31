<script setup lang="ts">
/**
 * Iframe 嵌入面板：按地址嵌入外部页面，无数据集依赖。
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

const url = computed(() => (props.panel.options?.url as string) || '')
</script>

<template>
  <div class="iframe-panel">
    <span v-if="!url" class="iframe-empty">在配置面板填写嵌入地址</span>
    <iframe v-else class="iframe-el" :src="url" frameborder="0" />
  </div>
</template>

<style scoped lang="scss">
.iframe-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  overflow: hidden;
}

.iframe-el {
  width: 100%;
  height: 100%;
  border: none;
}

.iframe-empty {
  font-size: 13px;
  color: #bbb;
}
</style>

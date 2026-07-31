<script setup lang="ts">
/**
 * 文本面板：展示文本（保留换行），支持绑定数据集后用 {{ 字段 }} / {{#each}} 动态占位。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'
import { interpolateTemplate } from '../templateInterpolate'

const props = defineProps<{
  panel: PanelDsl
  data?: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const content = computed(() =>
  interpolateTemplate((props.panel.options?.content as string) || '', props.data ?? null),
)
const align = computed<'left' | 'center' | 'right'>(
  () => ((props.panel.options?.align as string) || 'left') as 'left' | 'center' | 'right',
)
</script>

<template>
  <div class="text-panel" :style="{ textAlign: align }">
    <span v-if="content" class="text-content">{{ content }}</span>
    <span v-else class="text-empty">在配置面板填写文本内容</span>
  </div>
</template>

<style scoped lang="scss">
.text-panel {
  height: 100%;
  overflow: auto;
}

.text-content {
  font-size: var(--dash-text-size, 14px);
  color: var(--dash-text-color, #434343);
  font-weight: var(--dash-text-weight, 400);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.text-empty {
  font-size: 13px;
  color: #bbb;
}
</style>

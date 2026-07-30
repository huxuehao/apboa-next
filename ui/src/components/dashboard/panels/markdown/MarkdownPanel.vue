<script setup lang="ts">
/**
 * Markdown 卡片：渲染 Markdown 富文本（标题/列表/代码/链接等），安全净化。无数据集依赖。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { DatasetExecuteResult, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  data?: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
}>()

const content = computed(() => (props.panel.options?.content as string) || '')

const html = computed(() => {
  if (!content.value) return ''
  const raw = marked.parse(content.value, { async: false }) as string
  return DOMPurify.sanitize(raw)
})
</script>

<template>
  <div class="markdown-panel">
    <div v-if="html" class="md-body" v-html="html" />
    <span v-else class="md-empty">在配置面板填写 Markdown 内容</span>
  </div>
</template>

<style scoped lang="scss">
.markdown-panel {
  height: 100%;
  overflow: auto;
}

.md-empty {
  font-size: 13px;
  color: #bbb;
}

.md-body {
  font-size: 14px;
  color: #434343;
  line-height: 1.7;
  word-break: break-word;

  :deep(h1),
  :deep(h2),
  :deep(h3) {
    margin: 12px 0 8px;
    font-weight: 600;
    color: #1a1a1a;
  }

  :deep(h1) {
    font-size: 18px;
  }

  :deep(h2) {
    font-size: 16px;
  }

  :deep(h3) {
    font-size: 14px;
  }

  :deep(p) {
    margin: 6px 0;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 20px;
    margin: 6px 0;
  }

  :deep(a) {
    color: #1677ff;
  }

  :deep(code) {
    padding: 1px 5px;
    border-radius: 4px;
    background: #f2f3f5;
    font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
    font-size: 12px;
  }

  :deep(pre) {
    padding: 10px 12px;
    border: 1px solid #eee;
    border-radius: 6px;
    background: #f8f9fa;
    overflow-x: auto;

    code {
      padding: 0;
      background: transparent;
    }
  }

  :deep(blockquote) {
    margin: 8px 0;
    padding: 4px 12px;
    border-left: 3px solid #e8e8e8;
    color: #8c8c8c;
  }

  :deep(table) {
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 4px 10px;
    border: 1px solid #f0f0f0;
  }
}
</style>

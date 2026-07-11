<template>
  <div ref="container" class="markdown-renderer">
    <template v-for="(part, index) in parts" :key="index">
      <div v-if="part.type === 'html'" v-html="part.content"></div>
      <MermaidRenderer
        v-else-if="part.type === 'mermaid'"
        :code="part.code as string"
        :is-streaming="isStreaming"
      />
      <UIPRenderer
        v-else-if="part.type === 'uip'"
        :code="part.code as string"
        :is-streaming="isStreaming"
        :disabled="disabled"
        @submit="emit('interactionSubmit', $event)"
        @retry="emit('uipRetry', $event)"
      />
      <VEPRenderer
        v-else-if="part.type === 'vep'"
        :code="part.code as string"
        :is-streaming="isStreaming"
        :disabled="disabled"
        @retry="emit('vepRetry', $event)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { renderMarkdown } from '@/utils/chat/markdown'
import MermaidRenderer from '@/components/markdown/mermaid/MermaidRenderer.vue'
import UIPRenderer from '@/components/markdown/uip/UIPRenderer.vue'
import VEPRenderer from '@/components/markdown/vep/VEPRenderer.vue'
import { extractUIPBlocks } from '@/utils/chat/uip.ts'
import { extractVEPBlocks } from '@/utils/chat/vep'
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'

const props = defineProps<{
  content: string
  disabled?: boolean
  isStreaming?: boolean
}>()

const emit = defineEmits<{
  interactionSubmit: [payload: InteractionSubmitPayload]
  uipRetry: [uipCode: string]
  vepRetry: [vepCode: string]
}>()

const container = ref<HTMLElement>()

/** 内容分片：html、mermaid 图表、uip 交互组件、vep 视觉增强 */
type ContentPart =
  | { type: 'html'; content?: string; code?: string }
  | { type: 'mermaid'; content?: string; code?: string }
  | { type: 'uip'; content?: string; code?: string }
  | { type: 'vep'; content?: string; code?: string }

const parts = ref<ContentPart[]>([])

// 提取 mermaid 代码块
const extractMermaidBlocks = (html: string) => {
  const blocks: Array<{ placeholder: string; code: string; fullMatch: string }> = []

  // 匹配 mermaid 块的正则表达式
  const mermaidRegex = /<(pre|div)\s+class="mermaid"[^>]*>([\s\S]*?)<\/\1>/gi

  let match: RegExpExecArray | null
  let blockIndex = 0

  while ((match = mermaidRegex.exec(html)) !== null) {
    // 现在 TypeScript 知道 match 不是 null
    const fullMatch = match[0]
    const code = match[2]?.trim() || ''
    const placeholder = `__MERMAID_PLACEHOLDER_${blockIndex}__`

    blocks.push({
      placeholder,
      code,
      fullMatch
    })

    blockIndex++
  }

  return blocks
}

const parseContent = () => {
  if (!props.content) {
    parts.value = []
    return
  }

  // 先渲染为 HTML
  const html = renderMarkdown(props.content)

  // 提取 mermaid 块
  const mermaidBlocks = extractMermaidBlocks(html)
  // 提取 uip 块
  const uipBlocks = extractUIPBlocks(html)
  // 提取 vep 块
  const vepBlocks = extractVEPBlocks(html)

  if (mermaidBlocks.length === 0 && uipBlocks.length === 0 && vepBlocks.length === 0) {
    // 没有特殊块，直接使用 HTML
    parts.value = [{ type: 'html', content: html }]
    return
  }

  // 合并所有特殊块，按在 HTML 中的出现顺序排序
  interface SpecialBlock {
    type: 'mermaid' | 'uip' | 'vep'
    fullMatch: string
    code: string
    startIndex: number
  }
  const specialBlocks: SpecialBlock[] = []

  for (const b of mermaidBlocks) {
    const idx = html.indexOf(b.fullMatch)
    if (idx >= 0) specialBlocks.push({ type: 'mermaid', fullMatch: b.fullMatch, code: b.code, startIndex: idx })
  }
  for (const b of uipBlocks) {
    const idx = html.indexOf(b.fullMatch)
    if (idx >= 0) specialBlocks.push({ type: 'uip', fullMatch: b.fullMatch, code: b.code, startIndex: idx })
  }
  for (const b of vepBlocks) {
    const idx = html.indexOf(b.fullMatch)
    if (idx >= 0) specialBlocks.push({ type: 'vep', fullMatch: b.fullMatch, code: b.code, startIndex: idx })
  }

  // 按出现位置排序
  specialBlocks.sort((a, b) => a.startIndex - b.startIndex)

  // 按出现顺序分割
  let currentHtml = html
  const newParts: ContentPart[] = []

  for (const block of specialBlocks) {
    const index = currentHtml.indexOf(block.fullMatch)
    if (index === -1) continue

    if (index > 0) {
      newParts.push({ type: 'html', content: currentHtml.substring(0, index) })
    }

    newParts.push({ type: block.type, code: block.code })
    currentHtml = currentHtml.substring(index + block.fullMatch.length)
  }

  if (currentHtml && currentHtml.trim()) {
    newParts.push({ type: 'html', content: currentHtml })
  }

  parts.value = newParts
}

onMounted(() => {
  parseContent()
})

watch(() => props.content, () => {
  nextTick(() => parseContent())
})
</script>

<style scoped>
.markdown-renderer {
  width: 100%;
}
</style>

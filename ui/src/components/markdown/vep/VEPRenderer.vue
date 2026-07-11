<template>
  <div class="vep-renderer">
    <!-- 流式 JSON 不完整 -> 骨架屏 -->
    <VEPSkeleton v-if="!parsed && isStreaming" />

    <!-- 非流式 JSON 无效 -> 降级显示 -->
    <div v-else-if="!parsed" class="vep-fallback">
      <div class="vep-fallback-hint">[VEP 解析失败]</div>
      <pre class="vep-fallback-code">{{ truncatedCode }}</pre>
      <button
        v-if="!disabled"
        class="vep-retry-btn"
        :disabled="retrying"
        @click="onRetry"
      >
        <ReloadOutlined /> {{ retrying ? '重试中...' : '重新生成' }}
      </button>
    </div>

    <!-- vision 为空数组 -> 提示 -->
    <div v-else-if="!parsed.vision" class="vep-fallback">
      <div class="vep-fallback-hint">[VEP 视觉内容为空]</div>
      <button
        v-if="!disabled"
        class="vep-retry-btn"
        :disabled="retrying"
        @click="onRetry"
      >
        <ReloadOutlined /> {{ retrying ? '重试中...' : '重新生成' }}
      </button>
    </div>

    <!-- 未知视觉类型 -> 降级 -->
    <div v-else-if="parsed.vision.type !== 'card' && parsed.vision.type !== 'chart'" class="vep-fallback">
      <div class="vep-fallback-hint">[VEP 未知视觉类型]</div>
      <button
        v-if="!disabled"
        class="vep-retry-btn"
        :disabled="retrying"
        @click="onRetry"
      >
        <ReloadOutlined /> {{ retrying ? '重试中...' : '重新生成' }}
      </button>
    </div>

    <!-- 逐组件渲染 -->
    <template v-else>
      <CardRenderer v-if="parsed.vision.type === 'card'" :item="parsed.vision" />
      <ChartRenderer v-else-if="parsed.vision.type === 'chart'" :item="parsed.vision" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import type { VEPMessage } from './types'
import { parseVEPJson } from '@/utils/chat/vep'
import VEPSkeleton from './VEPSkeleton.vue'
import CardRenderer from './CardRenderer.vue'
import ChartRenderer from './ChartRenderer.vue'

const props = defineProps<{
  /** vep 代码块原始内容（不含 ``` 标记） */
  code: string
  /** 是否处于流式输出阶段 */
  isStreaming?: boolean
  /** 历史消息只读模式 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  retry: [code: string]
}>()

const parsed = computed<VEPMessage | null>(() => parseVEPJson(props.code))

const truncatedCode = computed(() => {
  if (props.code.length <= 300) return props.code
  return props.code.slice(0, 300) + '...'
})

const retrying = ref(false)

/** 触发重试，请求智能体重新生成视觉卡片 */
function onRetry() {
  if (retrying.value) return
  retrying.value = true
  emit('retry', props.code)
  // 5秒后重置状态，允许再次点击
  setTimeout(() => { retrying.value = false }, 5000)
}
</script>

<style scoped>
.vep-renderer {
  margin: 4px 0;
}

.vep-fallback {
  margin: 8px 0;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  padding: 10px 14px;
  background: #fffbe6;
}

.vep-fallback-hint {
  font-size: 12px;
  color: #d48806;
  margin-bottom: 6px;
}

.vep-fallback-code {
  font-size: 11px;
  color: #8c8c8c;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
  margin: 0;
  line-height: 1.5;
}

.vep-retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 4px 12px;
  font-size: 12px;
  color: #1677ff;
  background: #fff;
  border: 1px solid #1677ff;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    color: #fff;
    background: #1677ff;
  }

  &:disabled {
    color: #bfbfbf;
    border-color: #d9d9d9;
    cursor: not-allowed;
  }
}
</style>

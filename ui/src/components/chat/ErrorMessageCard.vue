/**
 * 运行错误消息卡片
 *
 * 替代裸红字：警示图标 + 翻译后的中文标题 + 处理建议，
 * 已知模式的英文原文收进「详情」折叠区供排查；
 * 未知错误以卡片形态兜底、原文作为正文展示。
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ExclamationCircleFilled, RightOutlined, DownOutlined } from '@ant-design/icons-vue'
import { translateErrorMessage } from '@/utils/chat/errorMessage'

const props = defineProps<{
  /** error 消息原始内容（异常 message） */
  content: string
}>()

const friendly = computed(() => translateErrorMessage(props.content))
const detailExpanded = ref(false)
</script>

<template>
  <div class="error-message-card">
    <div class="error-card-main">
      <ExclamationCircleFilled class="error-card-icon" />
      <div class="error-card-body">
        <div class="error-card-title">{{ friendly.title }}</div>
        <div v-if="friendly.advice" class="error-card-advice">{{ friendly.advice }}</div>
        <!-- 未匹配已知模式：原文即正文 -->
        <div v-if="!friendly.matched && content" class="error-card-raw-inline">{{ content }}</div>
      </div>
    </div>
    <!-- 已匹配模式：原文收进详情，供排查定位 -->
    <div v-if="friendly.matched" class="error-card-detail">
      <span class="error-card-detail-toggle" @click="detailExpanded = !detailExpanded">
        <DownOutlined v-if="detailExpanded" />
        <RightOutlined v-else />
        详情
      </span>
      <pre v-show="detailExpanded" class="error-card-detail-raw">{{ content }}</pre>
    </div>
  </div>
</template>

<style scoped lang="scss">
.error-message-card {
  padding: 12px 14px;
  background-color: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: var(--border-radius-md);
  max-width: 100%;
  overflow-wrap: break-word;
}

.error-card-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.error-card-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 16px;
  color: var(--color-danger);
}

.error-card-body {
  flex: 1;
  min-width: 0;
}

.error-card-title {
  font-size: var(--font-size-base);
  font-weight: 500;
  color: var(--color-text-primary);
}

.error-card-advice {
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.error-card-raw-inline {
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  word-break: break-word;
}

.error-card-detail {
  margin-top: 8px;
  padding-left: 26px;
}

.error-card-detail-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  cursor: pointer;
  user-select: none;

  &:hover {
    color: var(--color-text-regular);
  }

  .anticon {
    font-size: 10px;
  }
}

.error-card-detail-raw {
  margin: 6px 0 0;
  padding: 8px 10px;
  background-color: rgba(0, 0, 0, 0.04);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 160px;
  overflow-y: auto;
}
</style>

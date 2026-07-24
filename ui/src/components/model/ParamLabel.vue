<script setup lang="ts">
/**
 * 参数标签 + 悬停说明（i 图标，鼠标悬停展示参数解释与设置建议）
 * 文案集中在 constants/modelParamTips.ts；说明成段、建议逐行
 * 提示内容用内联样式：ATooltip 会 teleport 到 body，scoped 样式无法命中
 *
 * @author vaulka
 */
import { computed } from 'vue'
import { InfoCircleOutlined } from '@ant-design/icons-vue'
import { MODEL_PARAM_TIPS } from '@/constants/modelParamTips'

const props = defineProps<{ param: string }>()

const info = computed(() => MODEL_PARAM_TIPS[props.param] ?? { label: props.param, desc: '', suggest: [] })
</script>

<template>
  <span class="param-label">
    <span>{{ info.label }}</span>
    <ATooltip v-if="info.desc" placement="topLeft" :overlay-style="{ maxWidth: '380px' }">
      <template #title>
        <div style="font-size: 12.5px; line-height: 1.7;">
          <div style="margin-bottom: 6px;">{{ info.desc }}</div>
          <template v-if="info.suggest && info.suggest.length">
            <div style="opacity: 0.6; margin-bottom: 3px;">建议</div>
            <div
              v-for="(s, i) in info.suggest"
              :key="i"
              style="padding-left: 11px; text-indent: -11px; margin: 3px 0;"
            >· {{ s }}</div>
          </template>
        </div>
      </template>
      <InfoCircleOutlined class="param-label-icon" />
    </ATooltip>
  </span>
</template>

<style scoped lang="scss">
.param-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.param-label-icon {
  color: var(--color-text-placeholder, #a8abb2);
  cursor: help;
  font-size: 13px;
  transition: color 0.2s;

  &:hover {
    color: var(--color-primary, #0f74ff);
  }
}
</style>

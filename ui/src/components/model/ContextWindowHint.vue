<script setup lang="ts">
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  modelValue: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const presets = [
  { label: '200K', value: 200_000, description: '约 200,000' },
  { label: '400K', value: 400_000, description: '约 400,000' },
  { label: '1M', value: 1_000_000, description: '约 1,000,000' }
]

function applyPreset(value: number) {
  emit('update:modelValue', value)
}
</script>

<template>
  <ATooltip placement="top" trigger="hover">
    <template #title>
      <div class="context-window-help" @click.stop>
        <div class="help-title">上下文窗口是模型一次可处理的上下文 Token 上限</div>
        <div class="help-desc">不同模型的窗口大小不同，请以模型服务商文档为准。点击下面的常见值可直接填入：</div>
        <div class="preset-list">
          <AButton
            v-for="preset in presets"
            :key="preset.value"
            type="link"
            size="small"
            class="preset-button"
            @click="applyPreset(preset.value)"
          >
            <span>{{ preset.label }}</span>
            <small>{{ preset.description }}</small>
          </AButton>
        </div>
      </div>
    </template>
    <QuestionCircleOutlined class="context-window-help-icon" :aria-label="`当前 Context Window：${props.modelValue.toLocaleString()} Token`" />
  </ATooltip>
</template>

<style scoped lang="scss">
.context-window-help-icon { margin-left: 5px; color: #0068ff; cursor: help; }
.context-window-help { max-width: 330px; line-height: 1.5; }
.help-title { font-weight: 600; }
.help-desc { margin-top: 4px; color: rgba(255, 255, 255, .78); }
.preset-list { display: flex; gap: 4px; margin-top: 8px; width: 100%; }
.preset-button { display: inline-flex; height: auto; padding: 2px 5px; flex-direction: column; align-items: flex-start; line-height: 1.35; }
.preset-button small { color: rgba(255, 255, 255, .72); font-size: 11px; }
</style>

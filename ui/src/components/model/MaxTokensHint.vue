<script setup lang="ts">
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

defineProps<{
  modelValue: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const presets = [
  { label: '1K', value: 1_024, description: '约 1,024' },
  { label: '2K', value: 2_048, description: '约 2,048' },
  { label: '4K', value: 4_096, description: '约 4,096' },
  { label: '8K', value: 8_192, description: '约 8,192' },
  { label: '16K', value: 16_384, description: '约 16,384' },
  { label: '32K', value: 32_768, description: '约 32,768' }
]

function applyPreset(value: number) {
  emit('update:modelValue', value)
}
</script>

<template>
  <ATooltip placement="top" trigger="hover">
    <template #title>
      <div class="max-tokens-help" @click.stop>
        <div class="help-title">最大输出 Token 数</div>
        <div class="help-desc">
          限制模型单次回答最多生成多少 Token。值越大，回答可以更长，但会占用更多上下文预算和推理时间。点击下方常见值可直接填入：
        </div>
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
    <QuestionCircleOutlined class="max-tokens-help-icon" aria-label="最大输出 Token 数说明" />
  </ATooltip>
</template>

<style scoped lang="scss">
.max-tokens-help-icon { margin-left: 5px; color: #0068ff; cursor: help; }
.max-tokens-help { max-width: 330px; line-height: 1.5; }
.help-title { font-weight: 600; }
.help-desc { margin-top: 4px; color: rgba(255, 255, 255, .78); }
.preset-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3px; margin-top: 8px; width: 100%;}
.preset-button { display: inline-flex; height: auto; padding: 2px 5px; flex-direction: column; align-items: flex-start; line-height: 1.35; }
.preset-button small { color: rgba(255, 255, 255, .72); font-size: 11px; }
</style>

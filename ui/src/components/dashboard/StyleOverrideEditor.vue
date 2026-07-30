<script setup lang="ts">
/**
 * 样式覆盖编辑器：仅编辑白名单样式键，遵循简约灰阶规范（禁渐变/大阴影）。
 *
 * @author huxuehao
 */
const props = defineProps<{ styleValue: Record<string, string> }>()
const emit = defineEmits<{ (e: 'update:styleValue', value: Record<string, string>): void }>()

const fontWeightOptions = [
  { label: '常规', value: 'normal' },
  { label: '中等', value: '500' },
  { label: '加粗', value: '600' },
]

const textAlignOptions = [
  { label: '左对齐', value: 'left' },
  { label: '居中', value: 'center' },
  { label: '右对齐', value: 'right' },
]

function set(key: string, value: string | number | undefined) {
  const next = { ...props.styleValue }
  if (value === undefined || value === '' || value === null) {
    delete next[key]
  } else {
    next[key] = String(value)
  }
  emit('update:styleValue', next)
}
</script>

<template>
  <div class="style-editor">
    <div class="style-row">
      <span class="style-label">背景色</span>
      <input
        type="color"
        class="color-input"
        :value="styleValue.backgroundColor || '#ffffff'"
        @input="set('backgroundColor', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <div class="style-row">
      <span class="style-label">文字色</span>
      <input
        type="color"
        class="color-input"
        :value="styleValue.color || '#1a1a1a'"
        @input="set('color', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <div class="style-row">
      <span class="style-label">字号</span>
      <a-input
        :value="styleValue.fontSize"
        placeholder="如 14px"
        allow-clear
        @update:value="set('fontSize', $event)"
      />
    </div>
    <div class="style-row">
      <span class="style-label">字重</span>
      <a-select
        :value="styleValue.fontWeight"
        :options="fontWeightOptions"
        placeholder="默认"
        allow-clear
        style="width: 100%"
        @update:value="set('fontWeight', $event)"
      />
    </div>
    <div class="style-row">
      <span class="style-label">对齐</span>
      <a-select
        :value="styleValue.textAlign"
        :options="textAlignOptions"
        placeholder="默认"
        allow-clear
        style="width: 100%"
        @update:value="set('textAlign', $event)"
      />
    </div>
    <div class="style-row">
      <span class="style-label">圆角</span>
      <a-input
        :value="styleValue.borderRadius"
        placeholder="如 8px"
        allow-clear
        @update:value="set('borderRadius', $event)"
      />
    </div>
    <div class="style-row">
      <span class="style-label">内边距</span>
      <a-input
        :value="styleValue.padding"
        placeholder="如 12px"
        allow-clear
        @update:value="set('padding', $event)"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.style-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.style-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.style-label {
  flex-shrink: 0;
  width: 48px;
  font-size: 13px;
  color: #595959;
}

.color-input {
  width: 48px;
  height: 28px;
  padding: 0;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
</style>

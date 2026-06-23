<template>
  <div class="uip-choice-renderer">
    <div class="uip-choice-question">
      <span>{{ interaction.question }}</span>
      <span v-if="disabled && interaction.submittedData" class="uip-choice-status">已完成</span>
    </div>
    <div class="uip-choice-body">
      <a-radio-group
        v-if="!interaction.multiple"
        :value="selectedValues[0]"
        :disabled="disabled"
        class="uip-choice-group"
        @update:value="onSingleChange"
      >
        <div
          v-for="opt in interaction.options"
          :key="opt.value"
          class="uip-choice-option"
          :class="{ 'is-disabled': opt.disabled || disabled }"
        >
          <a-radio :value="opt.value" :disabled="opt.disabled || disabled">
            <span class="uip-choice-label">{{ opt.label }}</span>
            <span v-if="opt.description" class="uip-choice-desc">{{ opt.description }}</span>
          </a-radio>
        </div>
      </a-radio-group>

      <a-checkbox-group
        v-else
        :value="selectedValues"
        :disabled="disabled"
        class="uip-choice-group"
        @update:value="onMultiChange"
      >
        <div
          v-for="opt in interaction.options"
          :key="opt.value"
          class="uip-choice-option"
          :class="{ 'is-disabled': opt.disabled || disabled }"
        >
          <a-checkbox :value="opt.value" :disabled="opt.disabled || disabled">
            <span class="uip-choice-label">{{ opt.label }}</span>
            <span v-if="opt.description" class="uip-choice-desc">{{ opt.description }}</span>
          </a-checkbox>
        </div>
      </a-checkbox-group>

      <div v-if="interaction.allowCustom" class="uip-choice-custom">
        <a-input
          v-model:value="customInput"
          placeholder="输入自定义内容..."
          :disabled="disabled"
          class="uip-choice-custom-input"
        />
      </div>
    </div>

    <div v-if="!disabled" class="uip-choice-actions">
      <a-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
        确定
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ChoiceInteraction } from './types'

const props = defineProps<{
  interaction: ChoiceInteraction
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: [data: { values: string[]; customInput?: string }]
}>()

const submitting = ref(false)

/** 回填已提交数据 */
const selectedValues = ref<string[]>(
  props.interaction.submittedData?.values
    ? [...props.interaction.submittedData.values]
    : []
)

const customInput = ref(props.interaction.submittedData?.customInput || '')

const canSubmit = computed(() => {
  return selectedValues.value.length > 0 || customInput.value.trim().length > 0
})

const submittedSummary = computed(() => {
  const parts = [...selectedValues.value]
  if (customInput.value) parts.push(customInput.value)
  return parts.join('，')
})

function onSingleChange(val: unknown) {
  selectedValues.value = val ? [val as string] : []
}

function onMultiChange(val: unknown[]) {
  selectedValues.value = val as string[]
}

async function handleSubmit() {
  if (submitting.value || !canSubmit.value) return
  submitting.value = true
  emit('submit', {
    values: [...selectedValues.value],
    customInput: customInput.value.trim() || undefined,
  })
  // 不需将 submitting.value 只为 false，disabled 时会隐藏按钮
}
</script>

<style scoped>
.uip-choice-renderer {
  margin: 12px 0;
  border: 1px solid #e8ecf1;
  border-radius: 12px;
  padding: 18px 20px;
  background: #fafbfc;
}

.uip-choice-question {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f1f3;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.uip-choice-status {
  font-size: 12px;
  font-weight: 400;
  color: #52c41a;
  letter-spacing: 0.5px;
}

.uip-choice-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.uip-choice-option {
  padding: 8px 12px;
  border-radius: 8px;
  transition: background 0.15s;
}

.uip-choice-option:not(.is-disabled):hover {
  background: #f2f4f7;
}

.uip-choice-label {
  font-size: 14px;
  color: #1d2129;
}

.uip-choice-desc {
  display: block;
  font-size: 12px;
  color: #86909c;
  margin-top: 2px;
}

.uip-choice-custom {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #e8ecf1;
}

.uip-choice-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f1f3;
}

.uip-choice-summary {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f0f1f3;
  font-size: 13px;
  color: #86909c;
  display: flex;
  align-items: center;
  gap: 6px;
}

.uip-choice-summary-icon {
  flex-shrink: 0;
  color: #52c41a;
  font-weight: 700;
  font-size: 14px;
}
</style>

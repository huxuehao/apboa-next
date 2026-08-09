<template>
  <div class="uip-choice-renderer" :class="{ 'is-submitted': isSubmitted }">
    <!-- 折叠/展开 触发器 -->
    <button
      v-if="isSubmitted"
      type="button"
      class="uip-choice-collapse-trigger"
      @click="collapsed = !collapsed"
    >
      <div class="uip-choice-question uip-choice-question-clickable">
        <div class="uip-choice-question-left">
          <span>{{ interaction.question }}</span>
        </div>
        <div class="uip-choice-question-right">
          <span class="uip-choice-summary-text">{{ submittedSummary }}</span>
          <DownOutlined class="uip-choice-arrow" :class="{ 'is-collapsed': collapsed }" />
        </div>
      </div>
    </button>

    <!-- 未提交状态下的标题 -->
    <div v-else class="uip-choice-question">
      <span>{{ interaction.question }}</span>
    </div>

    <!-- 选择内容区域（带折叠动画） -->
    <Transition name="choice-collapse">
      <div v-show="!collapsed" class="uip-choice-content">
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
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { DownOutlined } from '@ant-design/icons-vue'
import type { ChoiceInteraction } from './types'

const props = defineProps<{
  interaction: ChoiceInteraction
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: [data: { values: string[]; customInput?: string }]
}>()

const submitting = ref(false)
const collapsed = ref(false)

// 判断是否已提交
const isSubmitted = computed(() =>
  !!(props.disabled && props.interaction.submittedData)
)

// 监听提交状态变化，自动折叠
watch(isSubmitted, (newVal) => {
  if (newVal) {
    collapsed.value = true
  }
})

// 初始化时检查状态
watch(
  () => props.interaction.submittedData,
  (newVal) => {
    if (newVal && props.disabled) {
      collapsed.value = true
    }
  },
  { immediate: true }
)

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

// 提交摘要显示
const submittedSummary = computed(() => {
  const parts: string[] = []

  // 获取选中选项的标签
  for (const val of selectedValues.value) {
    const option = props.interaction.options.find(opt => opt.value === val)
    if (option) {
      parts.push(option.label)
    } else {
      parts.push(val)
    }
  }

  // 添加自定义输入
  if (customInput.value) {
    parts.push(customInput.value)
  }

  const summary = parts.join('，')
  return summary.length > 30 ? summary.substring(0, 30) + '...' : summary
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
  // 不需将 submitting.value 设为 false，disabled 时会隐藏按钮
}
</script>

<style scoped>
.uip-choice-renderer {
  margin: 12px 0;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  background: #fafbfc;
  overflow: hidden;
  transition: all 0.3s ease;
}

.uip-choice-renderer.is-submitted {
  border-color: #e8ecf1;
}

/* 折叠触发器 */
.uip-choice-collapse-trigger {
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.uip-choice-collapse-trigger:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

.uip-choice-question {
  padding: 14px 20px;
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  border-bottom: 1px solid #f0f1f3;
}

.uip-choice-question-clickable {
  display: flex;
  align-items: center;
  justify-content: space-between;
  user-select: none;
}

.uip-choice-question-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.uip-choice-question-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 12px;
}

.uip-choice-status {
  font-size: 12px;
  font-weight: 400;
  color: #52c41a;
  letter-spacing: 0.5px;
  background: #f6ffed;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #b7eb8f;
  white-space: nowrap;
}

.uip-choice-summary-text {
  font-size: 13px;
  font-weight: 400;
  color: #8c8c8c;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.uip-choice-arrow {
  color: #8c8c8c;
  font-size: 12px;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.uip-choice-arrow.is-collapsed {
  transform: rotate(-90deg);
}

/* 选择内容区域 */
.uip-choice-content {
  padding: 0 20px 18px;
}

.uip-choice-body {
  padding-top: 8px;
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

.uip-choice-custom-input {
  width: 100%;
}

.uip-choice-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f1f3;
}

/* 折叠动画 */
.choice-collapse-enter-active,
.choice-collapse-leave-active {
  transition:
    max-height 0.3s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.25s ease,
    padding 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.choice-collapse-enter-from,
.choice-collapse-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.choice-collapse-enter-to,
.choice-collapse-leave-from {
  max-height: 2000px; /* 足够大的值 */
  opacity: 1;
}
</style>

<template>
  <div class="uip-form-renderer" :class="{ 'is-submitted': isSubmitted }">
    <!-- 折叠/展开 触发器 -->
    <button
      v-if="isSubmitted"
      type="button"
      class="uip-form-collapse-trigger"
      @click="collapsed = !collapsed"
    >
      <div class="uip-form-title uip-form-title-clickable">
        <div class="uip-form-title-left">
          <span>{{ interaction.props?.title || '表单' }}</span>
        </div>
        <div class="uip-form-title-right">
          <DownOutlined class="uip-form-arrow" :class="{ 'is-collapsed': collapsed }" />
        </div>
      </div>
    </button>

    <!-- 未提交状态或非折叠模式下的标题 -->
    <div v-else-if="interaction.props?.title" class="uip-form-title">
      <span>{{ interaction.props.title }}</span>
    </div>

    <!-- 表单内容区域（带折叠动画） -->
    <Transition name="form-collapse">
      <div v-show="!collapsed" class="uip-form-content">
        <a-form
          :model="formData"
          layout="vertical"
          class="uip-form"
        >
          <template v-for="field in visibleFields" :key="field.name">
            <component
              :is="fieldComponent(field.type)"
              :field="field"
              :model-value="formData[field.name]"
              :disabled="disabled"
              @update:model-value="onFieldChange(field.name, $event)"
            />
          </template>
        </a-form>
        <div v-if="!disabled" class="uip-form-actions">
          <a-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ interaction.props?.submitLabel || '提交' }}
          </a-button>
          <a-button v-if="showReset" style="margin-left: 8px" @click="handleReset">
            重置
          </a-button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { message } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'
import type { FormField, FormInteraction } from './types'
import { fieldRenderers } from './fields'
import TextField from './fields/TextField.vue'

const props = defineProps<{
  interaction: FormInteraction
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: [data: Record<string, unknown>]
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

/** 初始化表单数据（回填 defaultValue 或 submittedData） */
function initFormData(): Record<string, unknown> {
  const data: Record<string, unknown> = {}
  for (const f of props.interaction.fields) {
    if (props.interaction.submittedData && props.interaction.submittedData[f.name] !== undefined) {
      data[f.name] = props.interaction.submittedData[f.name]
    } else if (f.defaultValue !== undefined) {
      data[f.name] = f.defaultValue
    } else {
      // 根据类型给合理的默认值
      data[f.name] = getDefaultForType(f)
    }
  }
  return data
}

function getDefaultForType(f: FormField): unknown {
  if (f.type === 'checkbox' || f.type === 'checkbox-group') return []
  if (f.type === 'switch') return false
  if (f.type === 'number') return undefined
  return ''
}

const formData = reactive<Record<string, unknown>>(initFormData())

/** 根据联动规则过滤可见字段 */
const allFields = computed(() => props.interaction.fields)

const visibleFields = computed(() => {
  return allFields.value.filter((f) => {
    if (f.dependsOn) {
      const depValue = formData[f.dependsOn.field]
      return depValue === f.dependsOn.value
    }
    return !f.hidden
  })
})

// 依赖字段值变化时，清理不可见字段的值
watch(
  () => formData,
  () => {
    for (const f of allFields.value) {
      if (f.dependsOn) {
        const depValue = formData[f.dependsOn.field]
        if (depValue !== f.dependsOn.value && formData[f.name] !== undefined) {
          formData[f.name] = getDefaultForType(f)
        }
      }
    }
  },
  { deep: true }
)

const showReset = computed(() => props.interaction.fields.length <= 4)

/** 动态获取字段组件，未知类型降级为 TextField */
function fieldComponent(type: string) {
  const cmp = fieldRenderers[type]
  if (!cmp) {
    console.warn(`[UIP] 未知表单字段类型: ${type}，已降级为 text`)
    return TextField
  }
  return cmp
}

function onFieldChange(name: string, value: unknown) {
  formData[name] = value
}

/** 收集可见字段的提交数据 */
function collectSubmitData(): Record<string, unknown> {
  const data: Record<string, unknown> = {}
  for (const f of visibleFields.value) {
    const val = formData[f.name]
    if (val !== undefined && val !== null && val !== '') {
      data[f.name] = val
    }
  }
  return data
}

/** 校验字段值，返回首个错误消息，无错误返回 null */
function validateField(field: FormField, value: unknown): string | null {
  const isEmpty = value === undefined || value === null || value === ''
  const isArrayEmpty = Array.isArray(value) && value.length === 0

  if (field.required && (isEmpty || isArrayEmpty)) {
    return `${field.label}为必填项`
  }

  const rules = field.validations
  if (!rules || isEmpty) return null

  for (const rule of rules) {
    if (rule.type === 'required' && isEmpty) {
      return rule.message || `${field.label}为必填项`
    }
    if (rule.type === 'min' && typeof rule.value === 'number' && Number(value) < rule.value) {
      return rule.message || `${field.label}不能小于${rule.value}`
    }
    if (rule.type === 'max' && typeof rule.value === 'number' && Number(value) > rule.value) {
      return rule.message || `${field.label}不能大于${rule.value}`
    }
    if (rule.type === 'pattern' && rule.value && typeof value === 'string') {
      try {
        const regex = new RegExp(String(rule.value))
        if (!regex.test(value)) {
          return rule.message || `${field.label}格式不正确`
        }
      } catch {
        // 正则无效则跳过
      }
    }
  }
  return null
}

async function handleSubmit() {
  if (submitting.value) return

  // 校验所有可见字段
  for (const field of visibleFields.value) {
    const error = validateField(field, formData[field.name])
    if (error) {
      message.warning(error)
      return
    }
  }

  submitting.value = true
  const data = collectSubmitData()
  emit('submit', data)
  // 不需将 submitting.value 只为 false，disabled 时会隐藏按钮
}

function handleReset() {
  for (const f of props.interaction.fields) {
    formData[f.name] = f.defaultValue !== undefined ? f.defaultValue : getDefaultForType(f)
  }
}
</script>

<style scoped>
.uip-form-renderer {
  margin: 12px 0;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  background: #fafbfc;
  overflow: hidden;
  transition: all 0.3s ease;
}


/* 折叠触发器 */
.uip-form-collapse-trigger {
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.uip-form-title {
  padding: 14px 20px;
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  border-bottom: 1px solid #f0f1f3;
}

.uip-form-title-clickable {
  display: flex;
  align-items: center;
  justify-content: space-between;
  user-select: none;
}

.uip-form-title-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.uip-form-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.uip-form-arrow {
  color: #8c8c8c;
  font-size: 12px;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.uip-form-arrow.is-collapsed {
  transform: rotate(-90deg);
}

.uip-form-content {
  padding: 0 20px 18px;
}

.uip-form {
  margin-bottom: 4px;
  padding-top: 8px;
}

.uip-form-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f1f3;
}

/* 折叠动画 */
.form-collapse-enter-active,
.form-collapse-leave-active {
  transition:
    max-height 0.3s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.25s ease,
    padding 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.form-collapse-enter-from,
.form-collapse-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.form-collapse-enter-to,
.form-collapse-leave-from {
  max-height: 2000px; /* 足够大的值 */
  opacity: 1;
}

</style>

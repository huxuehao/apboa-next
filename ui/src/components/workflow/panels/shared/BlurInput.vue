<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    maxlength?: number
    showCount?: boolean
    type?: string
    disabled?: boolean
  }>(),
  {
    modelValue: '',
    placeholder: '',
    type: 'text',
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const localValue = ref(props.modelValue)

watch(
  () => props.modelValue,
  (val) => {
    localValue.value = val
  },
)

function handleBlur() {
  if (localValue.value !== props.modelValue) {
    emit('update:modelValue', localValue.value)
  }
}
</script>

<template>
  <AInput
    v-model:value="localValue"
    :placeholder="placeholder"
    :maxlength="maxlength"
    :show-count="showCount"
    :type="type"
    :disabled="disabled"
    @blur="handleBlur"
  />
</template>

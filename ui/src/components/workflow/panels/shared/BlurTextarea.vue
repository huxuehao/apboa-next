<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    rows?: number
    autoSize?: { minRows: number; maxRows: number }
  }>(),
  {
    modelValue: '',
    placeholder: '',
    rows: 3,
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
  <ATextarea
    v-model:value="localValue"
    :placeholder="placeholder"
    :rows="rows"
    :auto-size="autoSize"
    @blur="handleBlur"
  />
</template>

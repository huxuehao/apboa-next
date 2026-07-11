<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const localValue = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  localValue.value = val
})

function handleBlur() {
  if (localValue.value !== props.modelValue) {
    emit('update:modelValue', localValue.value)
  }
}
</script>

<template>
  <AFormItem label="" required>
    <AInput
      v-model:value="localValue"
      placeholder="输入节点名称"
      :maxlength="50"
      show-count
      @blur="handleBlur"
    />
  </AFormItem>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <AFormItem label="" required>
    <AInput
      :value="modelValue"
      placeholder="输入节点名称"
      :maxlength="50"
      show-count
      @update:value="(value: string) => emit('update:modelValue', value)"
    />
  </AFormItem>
</template>

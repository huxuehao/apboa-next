<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value?: string
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:value', value: string): void
}>()

const editorPlaceholder = computed(() => props.placeholder || '请输入 JSON 扩展配置，例如：{"timeout": 5000}')

function updateValue(value: string) {
  emit('update:value', value)
}
</script>

<template>
  <ATextarea
    :value="value"
    :rows="5"
    :placeholder="editorPlaceholder"
    class="resource-json-editor"
    @update:value="updateValue"
  />
</template>

<style scoped lang="scss">
.resource-json-editor {
  font-family: "SF Mono", "Cascadia Code", "Fira Code", monospace;
  font-size: 12px;
}
</style>

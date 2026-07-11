<script setup lang="ts">
import { computed } from 'vue'
import BlurInput from '@/components/workflow/panels/shared/BlurInput.vue'
import BlurTextarea from '@/components/workflow/panels/shared/BlurTextarea.vue'
import WorkflowArrayEditors from './WorkflowArrayEditors.vue'

const props = defineProps<{
  modelValue?: unknown
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

const request = computed<Record<string, unknown>>(() => {
  if (props.modelValue && typeof props.modelValue === 'object') return props.modelValue as Record<string, unknown>
  return { method: 'GET', contentType: 'JSON', pathParams: [], queryParams: [], headers: [], body: '' }
})

function update(key: string, value: unknown) {
  emit('update:modelValue', { ...request.value, [key]: value })
}
</script>

<template>
  <div class="http-editor">
    <div class="http-line">
      <ASelect
        :value="request.method || 'GET'"
        :options="['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'].map((value) => ({ label: value, value }))"
        @update:value="(value: string) => update('method', value)"
      />
      <BlurInput :model-value="String(request.url || '')" placeholder="https://api.example.com/users/${id}" @update:model-value="(value: string) => update('url', value)" />
    </div>

    <ASelect
      :value="request.contentType || 'JSON'"
      :options="[
        { label: 'JSON', value: 'JSON' },
        { label: 'Form UrlEncoded', value: 'FORM_URLENCODED' },
        { label: 'Form Data', value: 'FORM_DATA' },
        { label: 'XML', value: 'XML' },
        { label: 'Text Plain', value: 'TEXT_PLAIN' },
        { label: 'Octet Stream', value: 'OCTET_STREAM' },
      ]"
      @update:value="(value: string) => update('contentType', value)"
    />

    <ACollapse size="small" ghost>
      <ACollapsePanel key="path" header="路径参数">
        <WorkflowArrayEditors :model-value="request.pathParams" type="keyValue" @update:model-value="(value) => update('pathParams', value)" />
      </ACollapsePanel>
      <ACollapsePanel key="query" header="Query 参数">
        <WorkflowArrayEditors :model-value="request.queryParams" type="keyValue" @update:model-value="(value) => update('queryParams', value)" />
      </ACollapsePanel>
      <ACollapsePanel key="headers" header="请求头">
        <WorkflowArrayEditors :model-value="request.headers" type="keyValue" @update:model-value="(value) => update('headers', value)" />
      </ACollapsePanel>
    </ACollapse>

    <BlurTextarea
      :model-value="typeof request.body === 'string' ? request.body : JSON.stringify(request.body ?? '', null, 2)"
      :auto-size="{ minRows: 4, maxRows: 12 }"
      placeholder="请求 Body。当前后端 JSON body 接收字符串形式。"
      @update:model-value="(value: string) => update('body', value)"
    />
  </div>
</template>

<style scoped lang="scss">
.http-editor {
  display: grid;
  gap: 10px;
}

.http-line {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 8px;
}

@media (max-width: 720px) {
  .http-line {
    grid-template-columns: 1fr;
  }
}
</style>

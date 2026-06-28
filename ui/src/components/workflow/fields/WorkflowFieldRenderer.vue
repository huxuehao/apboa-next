<script setup lang="ts">
import SmartCodeEditor from '@/components/editor/SmartCodeEditor.vue'
import WorkflowArrayEditors from './WorkflowArrayEditors.vue'
import WorkflowCompareToEditor from './WorkflowCompareToEditor.vue'
import WorkflowHttpRequestEditor from './WorkflowHttpRequestEditor.vue'
import WorkflowResourceSelect from './WorkflowResourceSelect.vue'
import type { WorkflowFieldSchema, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  field: WorkflowFieldSchema
  value: unknown
  resources: WorkflowResourceMaps
  nodes: WorkflowFlowNode[]
  currentNodeId?: string
}>()

const emit = defineEmits<{
  change: [value: unknown]
}>()

function stringify(value: unknown) {
  if (value === undefined || value === null) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function parseJsonLike(value: string) {
  if (!value.trim()) return ''
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}
</script>

<template>
  <AFormItem :label="field.label" :required="field.required">
    <template #extra v-if="field.description">
      <span class="field-help">{{ field.description }}</span>
    </template>

    <AInput
      v-if="field.control === 'input'"
      :value="String(value ?? '')"
      :placeholder="field.placeholder"
      @update:value="(next: string) => emit('change', next)"
    />

    <ATextarea
      v-else-if="field.control === 'textarea'"
      :value="String(value ?? '')"
      :placeholder="field.placeholder"
      :auto-size="{ minRows: field.rows || 3, maxRows: 12 }"
      @update:value="(next: string) => emit('change', next)"
    />

    <AInputNumber
      v-else-if="field.control === 'number'"
      class="full-input"
      :value="typeof value === 'number' ? value : Number(value ?? field.defaultValue ?? 0)"
      :min="field.min"
      :max="field.max"
      @update:value="(next: number | null) => emit('change', next ?? field.defaultValue ?? null)"
    />

    <ASwitch
      v-else-if="field.control === 'switch'"
      :checked="Boolean(value)"
      @update:checked="(next: boolean) => emit('change', next)"
    />

    <ASelect
      v-else-if="field.control === 'select'"
      show-search
      allow-clear
      :value="value as any"
      :options="field.options"
      :placeholder="field.placeholder || '请选择'"
      @update:value="(next: string) => emit('change', next)"
    />

    <ASegmented
      v-else-if="field.control === 'segmented'"
      :value="value as any"
      :options="field.options || []"
      @update:value="(next: string) => emit('change', next)"
    />

    <SmartCodeEditor
      v-else-if="field.control === 'code'"
      :model-value="String(value ?? '')"
      :language="field.language || 'txt'"
      theme="light"
      height="220px"
      :show-change-language="false"
      :show-theme-toggle="false"
      :show-fullscreen="true"
      :placeholder="field.placeholder"
      @update:model-value="(next: string) => emit('change', next)"
    />

    <SmartCodeEditor
      v-else-if="field.control === 'json'"
      :model-value="stringify(value)"
      language="json"
      theme="light"
      height="220px"
      :show-change-language="false"
      :show-theme-toggle="false"
      :show-fullscreen="true"
      :placeholder="field.placeholder"
      @update:model-value="(next: string) => emit('change', parseJsonLike(next))"
    />

    <WorkflowResourceSelect
      v-else-if="field.control === 'resource' && field.resourceType"
      :model-value="String(value || '')"
      :resource-type="field.resourceType"
      :resources="resources"
      @update:model-value="(next) => emit('change', next)"
    />

    <WorkflowArrayEditors
      v-else-if="field.control === 'dbParams'"
      :model-value="value"
      type="dbParams"
      :options="field.options"
      @update:model-value="(next) => emit('change', next)"
    />

    <WorkflowArrayEditors
      v-else-if="field.control === 'keyValueList'"
      :model-value="value"
      type="keyValue"
      @update:model-value="(next) => emit('change', next)"
    />

    <WorkflowArrayEditors
      v-else-if="field.control === 'stringList'"
      :model-value="value"
      type="stringList"
      :options="field.options"
      @update:model-value="(next) => emit('change', next)"
    />

    <WorkflowArrayEditors
      v-else-if="field.control === 'startParams'"
      :model-value="value"
      type="startParams"
      :options="field.options"
      @update:model-value="(next) => emit('change', next)"
    />

    <WorkflowHttpRequestEditor
      v-else-if="field.control === 'httpRequest'"
      :model-value="value"
      @update:model-value="(next) => emit('change', next)"
    />

    <WorkflowCompareToEditor
      v-else-if="field.control === 'compareTo'"
      :model-value="value"
      :nodes="nodes"
      :current-node-id="currentNodeId"
      @update:model-value="(next) => emit('change', next)"
    />

    <WorkflowArrayEditors
      v-else-if="field.control === 'matchList'"
      :model-value="value"
      type="matchList"
      @update:model-value="(next) => emit('change', next)"
    />

    <ATextarea
      v-else
      :value="stringify(value)"
      :auto-size="{ minRows: 3, maxRows: 10 }"
      @update:value="(next: string) => emit('change', parseJsonLike(next))"
    />
  </AFormItem>
</template>

<style scoped lang="scss">
.field-help {
  display: block;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}

.full-input {
  width: 100%;
}
</style>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import * as workflowApi from '@/api/workflow'
import type { WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  modelValue?: string
  resourceType: 'cache' | 'datasource' | 'mq'
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const checking = ref(false)
const localValue = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  localValue.value = val
})

function handleChange(value: string) {
  localValue.value = value
  emit('update:modelValue', value)
}

const list = computed(() => {
  if (props.resourceType === 'cache') return props.resources.caches
  if (props.resourceType === 'datasource') return props.resources.datasources
  return props.resources.mqs
})

const selected = computed(() => list.value.find((item) => item.id === props.modelValue))

async function checkConnect() {
  if (!selected.value) {
    message.warning('请先选择资源')
    return
  }
  checking.value = true
  try {
    if (props.resourceType === 'cache') await workflowApi.checkCacheConnect(selected.value)
    if (props.resourceType === 'datasource') await workflowApi.checkDatasourceConnect(selected.value)
    if (props.resourceType === 'mq') await workflowApi.checkMqConnect(selected.value)
    message.success('连接检测通过')
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <div class="resource-select">
    <ASelect
      show-search
      allow-clear
      :value="localValue"
      :filter-option="(input: string, option: any) => String(option?.label || '').toLowerCase().includes(input.toLowerCase())"
      :options="list.map((item) => ({ label: item.name || item.id, value: item.id }))"
      placeholder="请选择已启用资源"
      @update:value="handleChange"
    />
    <AButton :loading="checking" :disabled="!selected" @click="checkConnect">
      <template #icon>
        <CheckCircleOutlined v-if="selected" />
        <ApiOutlined v-else />
      </template>
      检测
    </AButton>
  </div>
</template>

<style scoped lang="scss">
.resource-select {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}
</style>

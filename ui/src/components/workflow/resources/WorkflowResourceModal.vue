<script setup lang="ts">
import { onMounted, ref } from 'vue'
import WorkflowResourceMenu from './WorkflowResourceMenu.vue'
import WorkflowResourcePanel from './WorkflowResourcePanel.vue'
import * as workflowResourcesApi from '@/api/workflowResources'
import type { WorkflowResourceKind, WorkflowResourceSummary } from '@/types/workflowResources'

const emit = defineEmits<{
  (e: 'changed', summary: WorkflowResourceSummary): void
}>()

const currentMenu = ref<WorkflowResourceKind>('datasource')
const summary = ref<WorkflowResourceSummary>({
  total: 0,
  datasourceTotal: 0,
  cacheTotal: 0,
  mqTotal: 0,
  channelTotal: 0,
  datasourceEnabled: 0,
  cacheEnabled: 0,
  mqEnabled: 0,
  channelEnabled: 0
})

async function loadSummary() {
  const res = await workflowResourcesApi.summary()
  summary.value = res.data.data || summary.value
  emit('changed', summary.value)
}

onMounted(loadSummary)

defineExpose({ loadSummary })
</script>

<template>
  <div class="settings-container">
    <div class="settings-sidebar">
      <WorkflowResourceMenu v-model="currentMenu" :summary="summary" />
    </div>
    <div class="settings-divider"></div>
    <div class="settings-content">
      <WorkflowResourcePanel :kind="currentMenu" @changed="loadSummary" />
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/_settings.scss' as *;

.settings-container {
  min-height: 0;
}

.settings-content {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
</style>

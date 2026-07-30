<script setup lang="ts">
/**
 * 面板配置抽屉：数据集绑定、字段映射、声明式配置项、刷新、样式覆盖。
 * 采用 update(path,value) 事件上抛，由设计器统一应用变更（避免直接修改 prop）。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { getPanel } from './panels'
import StyleOverrideEditor from './StyleOverrideEditor.vue'
import IconSelect from './icons/IconSelect.vue'
import { datasetQuery } from '@/api/dashboard'
import type { DashboardDatasetEntity, PanelDsl } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl | null
  datasets: DashboardDatasetEntity[]
}>()

const emit = defineEmits<{ (e: 'update', path: string, value: unknown): void }>()

const definition = computed(() => (props.panel ? getPanel(props.panel.type) : undefined))
const needsDataset = computed(() => definition.value?.dataRequirement.needsDataset ?? false)
const supportsDataset = computed(() => definition.value?.dataRequirement.supportsDataset !== false)

const datasetOptions = computed(() =>
  props.datasets.map((d) => ({ label: d.name, value: d.id })),
)

const availableColumns = ref<string[]>([])
const columnLoading = ref(false)

function getByPath(path: string): unknown {
  if (!props.panel) return undefined
  return path.split('.').reduce<unknown>((acc, key) => {
    if (acc && typeof acc === 'object') return (acc as Record<string, unknown>)[key]
    return undefined
  }, props.panel)
}

/** 加载所绑定数据集的列，用于字段映射选择 */
async function loadColumns() {
  const datasetId = props.panel?.dataset?.id
  if (!datasetId) {
    availableColumns.value = []
    return
  }
  columnLoading.value = true
  try {
    const resp = await datasetQuery(datasetId, { limit: 1 })
    availableColumns.value = (resp.data.data.columns || []).map((c) => c.name)
  } catch {
    availableColumns.value = []
  } finally {
    columnLoading.value = false
  }
}

function onDatasetChange(id: string | undefined) {
  emit('update', 'dataset', id ? { id, params: {} } : null)
}

const columnOptions = computed(() => availableColumns.value.map((c) => ({ label: c, value: c })))

watch(
  () => props.panel?.dataset?.id,
  () => loadColumns(),
  { immediate: true },
)
</script>

<template>
  <div class="config-drawer">
    <div v-if="!panel" class="drawer-empty">选择一个面板进行配置</div>
    <template v-else>
      <div class="drawer-title">面板配置</div>

      <div class="config-section">
        <div class="config-item">
          <span class="config-label">显示标题</span>
          <a-switch
            :checked="panel.showTitle !== false"
            @update:checked="emit('update', 'showTitle', $event)"
          />
        </div>
        <div class="config-item">
          <span class="config-label">标题</span>
          <a-input
            :value="panel.title"
            :disabled="panel.showTitle === false"
            placeholder="面板标题"
            @update:value="emit('update', 'title', $event)"
          />
        </div>
      </div>

      <div v-if="supportsDataset" class="config-section">
        <div class="section-title">数据集</div>
        <div class="config-item">
          <span class="config-label">绑定</span>
          <div class="dataset-bind">
            <a-select
              :value="panel.dataset?.id"
              :options="datasetOptions"
              placeholder="选择数据集"
              allow-clear
              style="width: 100%"
              @update:value="onDatasetChange"
            />
            <a-button :loading="columnLoading" @click="loadColumns">
              <template #icon><ReloadOutlined /></template>
            </a-button>
          </div>
        </div>
      </div>

      <div v-if="definition?.configSchema?.length" class="config-section">
        <div class="section-title">显示与映射</div>
        <div v-for="field in definition.configSchema" :key="field.key" class="config-item">
          <span class="config-label">{{ field.label }}</span>
          <a-input
            v-if="field.type === 'text'"
            :value="getByPath(field.key) as string"
            :placeholder="field.placeholder"
            allow-clear
            @update:value="emit('update', field.key, $event)"
          />
          <a-textarea
            v-else-if="field.type === 'textarea'"
            :value="getByPath(field.key) as string"
            :placeholder="field.placeholder"
            :rows="4"
            @update:value="emit('update', field.key, $event)"
          />
          <a-input-number
            v-else-if="field.type === 'number'"
            :value="getByPath(field.key) as number"
            style="width: 100%"
            @update:value="emit('update', field.key, $event)"
          />
          <a-switch
            v-else-if="field.type === 'switch'"
            :checked="getByPath(field.key) as boolean"
            @update:checked="emit('update', field.key, $event)"
          />
          <IconSelect
            v-else-if="field.type === 'icon'"
            :value="(getByPath(field.key) as string)"
            @update:value="emit('update', field.key, $event)"
          />
          <a-select
            v-else-if="field.type === 'select'"
            :value="getByPath(field.key)"
            :options="field.options"
            allow-clear
            style="width: 100%"
            @update:value="emit('update', field.key, $event)"
          />
          <a-select
            v-else-if="field.type === 'field'"
            :value="getByPath(field.key)"
            :options="columnOptions"
            placeholder="选择列"
            allow-clear
            style="width: 100%"
            @update:value="emit('update', field.key, $event)"
          />
          <a-select
            v-else-if="field.type === 'fields'"
            mode="multiple"
            :value="getByPath(field.key)"
            :options="columnOptions"
            placeholder="选择列"
            allow-clear
            style="width: 100%"
            @update:value="emit('update', field.key, $event)"
          />
        </div>
        <div v-if="needsDataset && !availableColumns.length" class="config-hint">
          绑定数据集后可选择字段
        </div>
      </div>

      <div class="config-section">
        <div class="section-title">定时刷新</div>
        <div class="config-item">
          <span class="config-label">开启</span>
          <a-switch
            :checked="panel.refresh?.enabled"
            @update:checked="emit('update', 'refresh.enabled', $event)"
          />
        </div>
        <div class="config-item">
          <span class="config-label">间隔(秒)</span>
          <a-input-number
            :value="panel.refresh?.interval"
            :min="5"
            style="width: 100%"
            @update:value="emit('update', 'refresh.interval', $event)"
          />
        </div>
      </div>

      <div class="config-section">
        <div class="section-title">样式覆盖</div>
        <StyleOverrideEditor
          :style-value="panel.style || {}"
          @update:style-value="emit('update', 'style', $event)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.config-drawer {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 16px;
}

.drawer-empty {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: #999;
}

.drawer-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #595959;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}

.config-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-label {
  flex-shrink: 0;
  width: 56px;
  font-size: 13px;
  color: #595959;
}

.dataset-bind {
  display: flex;
  gap: 8px;
  flex: 1;
}

.config-hint {
  font-size: 12px;
  color: #bbb;
}
</style>

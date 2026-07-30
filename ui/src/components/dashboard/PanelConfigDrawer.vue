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
import FilterConfigModal from './filter/FilterConfigModal.vue'
import { buildFilterParams, initFilterValues } from './filter/filterParams'
import { getCachedColumns, setCachedColumns, invalidateCachedColumns } from './columnCache'
import { datasetQuery } from '@/api/dashboard'
import type { DashboardDatasetEntity, DashboardFilter, PanelDsl, PanelStyleGroup } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl | null
  datasets: DashboardDatasetEntity[]
}>()

const emit = defineEmits<{ (e: 'update', path: string, value: unknown): void }>()

const definition = computed(() => (props.panel ? getPanel(props.panel.type) : undefined))
const needsDataset = computed(() => definition.value?.dataRequirement.needsDataset ?? false)
const supportsDataset = computed(() => definition.value?.dataRequirement.supportsDataset !== false)
const supportsPanelFilters = computed(
  () => definition.value?.dataRequirement.supportsPanelFilters === true,
)

/** 样式覆盖分组：缺省 card + header，文字类面板额外声明 text */
const styleGroups = computed<PanelStyleGroup[]>(
  () => definition.value?.styleGroups || ['card', 'header'],
)

// ── 私有筛选配置 ──
const panelFilterConfigOpen = ref(false)

const filterPositionOptions = [
  { label: '标题栏右侧', value: 'header' },
  { label: '内容区左上', value: 'contentTopLeft' },
  { label: '内容区右上', value: 'contentTopRight' },
  { label: '内容区左下', value: 'contentBottomLeft' },
  { label: '内容区右下', value: 'contentBottomRight' },
]

const filterSizeOptions = [
  { label: '大', value: 'large' },
  { label: '中', value: 'middle' },
  { label: '小', value: 'small' },
]

/** 启用/关闭私有筛选；首次启用时一次性下发完整默认配置 */
function onTogglePanelFilter(enabled: boolean) {
  if (!props.panel) return
  if (!props.panel.panelFilter) {
    emit('update', 'panelFilter', {
      enabled,
      position: 'header',
      size: 'small',
      showLabel: true,
      items: [],
    })
  } else {
    emit('update', 'panelFilter.enabled', enabled)
  }
}

function onSavePanelFilterItems(items: DashboardFilter[]) {
  emit('update', 'panelFilter.items', items)
}

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

/** 加载所绑定数据集的列，用于字段映射选择。
 *  默认读列缓存（按 datasetId）避免重复取数；force=true 时绕过缓存强制重拉。
 *  取列时同样需带上数据集固定参数 + 私有筛选参数（默认值初始化，未选注入 NULL） */
async function loadColumns(force = false) {
  const datasetId = props.panel?.dataset?.id
  if (!datasetId) {
    availableColumns.value = []
    return
  }
  if (!force) {
    const cached = getCachedColumns(datasetId)
    if (cached) {
      availableColumns.value = cached
      return
    }
  }
  columnLoading.value = true
  try {
    const fixed = props.panel?.dataset?.params || {}
    const pf = props.panel?.panelFilter
    const filterParams = pf?.enabled ? buildFilterParams(pf.items, initFilterValues(pf.items)) : {}
    const resp = await datasetQuery(datasetId, {
      params: { ...fixed, ...filterParams },
      limit: 1,
    })
    const cols = (resp.data.data.columns || []).map((c) => c.name)
    availableColumns.value = cols
    setCachedColumns(datasetId, cols)
  } catch {
    availableColumns.value = []
  } finally {
    columnLoading.value = false
  }
}

function onDatasetChange(id: string | undefined) {
  // 绑定变更：失效新数据集缓存，使随后的 watch 取列拉取最新结构
  if (id) invalidateCachedColumns(id)
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
          <span class="config-label">显示标题栏</span>
          <a-switch
            :checked="panel.showHeader !== false"
            @update:checked="emit('update', 'showHeader', $event)"
          />
        </div>
        <div class="config-item">
          <span class="config-label">显示标题值</span>
          <a-switch
            :checked="panel.showTitle !== false"
            :disabled="panel.showHeader === false"
            @update:checked="emit('update', 'showTitle', $event)"
          />
        </div>
        <div class="config-item">
          <span class="config-label">标题</span>
          <a-input
            :value="panel.title"
            :disabled="panel.showHeader === false || panel.showTitle === false"
            placeholder="面板标题"
            @update:value="emit('update', 'title', $event)"
          />
        </div>
        <div class="config-item">
          <span class="config-label">标题图标</span>
          <IconSelect
            :value="panel.titleIcon"
            :disabled="panel.showHeader === false || panel.showTitle === false"
            @update:value="emit('update', 'titleIcon', $event)"
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
            <a-button :loading="columnLoading" @click="loadColumns(true)">
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
          <input
            v-else-if="field.type === 'color'"
            type="color"
            class="cfg-color"
            :value="(getByPath(field.key) as string) || '#1677ff'"
            @input="emit('update', field.key, ($event.target as HTMLInputElement).value)"
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

      <div v-if="supportsPanelFilters" class="config-section">
        <div class="section-title">私有筛选</div>
        <div class="config-item">
          <span class="config-label">启用</span>
          <a-switch
            :checked="panel.panelFilter?.enabled === true"
            @update:checked="onTogglePanelFilter"
          />
        </div>
        <template v-if="panel.panelFilter?.enabled">
          <div class="config-item">
            <span class="config-label">位置</span>
            <a-select
              :value="panel.panelFilter?.position || 'header'"
              :options="filterPositionOptions"
              style="width: 100%"
              @update:value="emit('update', 'panelFilter.position', $event)"
            />
          </div>
          <div class="config-item">
            <span class="config-label">尺寸</span>
            <a-select
              :value="panel.panelFilter?.size || 'small'"
              :options="filterSizeOptions"
              style="width: 100%"
              @update:value="emit('update', 'panelFilter.size', $event)"
            />
          </div>
          <div class="config-item">
            <span class="config-label">显示名称</span>
            <a-switch
              :checked="panel.panelFilter?.showLabel !== false"
              @update:checked="emit('update', 'panelFilter.showLabel', $event)"
            />
          </div>
          <a-button block @click="panelFilterConfigOpen = true">
            编辑筛选项（{{ panel.panelFilter?.items?.length || 0 }}）
          </a-button>
        </template>
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
          :groups="styleGroups"
          @update:style-value="emit('update', 'style', $event)"
        />
      </div>

      <FilterConfigModal
        v-model:open="panelFilterConfigOpen"
        :filters="panel.panelFilter?.items || []"
        @save="onSavePanelFilterItems"
      />
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
  width: 70px;
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

.cfg-color {
  width: 48px;
  height: 28px;
  padding: 0;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
</style>

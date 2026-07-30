<script setup lang="ts">
/**
 * 工作台设计器：三区式布局（面板库 / 画布 / 配置），支持拖拽、撤销重做、样式覆盖、保存个人副本。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { debounce } from 'lodash-es'
import {
  ArrowLeftOutlined,
  LeftOutlined,
  RightOutlined,
  RedoOutlined,
  ReloadOutlined,
  SaveOutlined,
  FilterOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons-vue'
import { registerBuiltinPanels, getPanel } from '@/components/dashboard/panels'
import DashboardCanvas from '@/components/dashboard/DashboardCanvas.vue'
import PanelLibrary from '@/components/dashboard/PanelLibrary.vue'
import PanelConfigDrawer from '@/components/dashboard/PanelConfigDrawer.vue'
import DatasetPanel from '@/components/dashboard/DatasetPanel.vue'
import DatasetHelp from '@/components/dashboard/DatasetHelp.vue'
import FilterBar from '@/components/dashboard/filter/FilterBar.vue'
import FilterConfigModal from '@/components/dashboard/filter/FilterConfigModal.vue'
import {
  buildFilterParams,
  initFilterValues,
  type FilterValues,
} from '@/components/dashboard/filter/filterParams'
import {
  dashboardPortal,
  dashboardSavePersonal,
  dashboardResetPersonal,
  datasetList,
} from '@/api/dashboard'
import { RouteNames } from '@/router/constants'
import type { DashboardDatasetEntity, DashboardDsl, PanelDsl } from '@/types/dashboard'

registerBuiltinPanels()

const router = useRouter()

const dsl = ref<DashboardDsl | null>(null)
const dashboardId = ref<string | null>(null)
const selectedId = ref<string | null>(null)
const datasets = ref<DashboardDatasetEntity[]>([])
const saving = ref(false)

// ── 全局筛选器 ──
const filterConfigOpen = ref(false)
const filterValues = ref<FilterValues>({})
const filters = computed(() => dsl.value?.filters || [])
const globalParams = computed(() => buildFilterParams(filters.value, filterValues.value))

function onSaveFilters(newFilters: typeof filters.value) {
  if (!dsl.value) return
  dsl.value.filters = newFilters
  filterValues.value = initFilterValues(newFilters)
  commit()
}

// ── 悬浮面板：左侧可隐藏，右侧可拖拽调宽 ──
const RIGHT_MIN = 310
const libraryOpen = ref(true)
const datasetPanelOpen = ref(false)
const rightWidth = ref(RIGHT_MIN)
const dragging = ref(false)
const maxRightWidth = computed(() => Math.max(RIGHT_MIN, Math.floor(window.innerWidth * 0.45)))

function beginResize(event: MouseEvent) {
  dragging.value = true
  const startX = event.clientX
  const startWidth = rightWidth.value
  const onMove = (e: MouseEvent) => {
    const next = startWidth + (startX - e.clientX)
    rightWidth.value = Math.max(RIGHT_MIN, Math.min(maxRightWidth.value, next))
  }
  const onUp = () => {
    dragging.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

const selectedPanel = computed<PanelDsl | null>(
  () => dsl.value?.panels.find((p) => p.id === selectedId.value) || null,
)

// ── 撤销/重做（命令模式：DSL 快照栈）──
const past = ref<string[]>([])
const future = ref<string[]>([])
let current = ''

function resetHistory() {
  past.value = []
  future.value = []
  current = dsl.value ? JSON.stringify(dsl.value) : ''
}

function commit() {
  if (!dsl.value) return
  const snapshot = JSON.stringify(dsl.value)
  if (snapshot === current) return
  past.value.push(current)
  current = snapshot
  future.value = []
}
const commitDebounced = debounce(commit, 400)

const canUndo = computed(() => past.value.length > 0)
const canRedo = computed(() => future.value.length > 0)

function undo() {
  if (!past.value.length) return
  future.value.push(current)
  current = past.value.pop() as string
  dsl.value = JSON.parse(current)
  selectedId.value = null
}

function redo() {
  if (!future.value.length) return
  past.value.push(current)
  current = future.value.pop() as string
  dsl.value = JSON.parse(current)
  selectedId.value = null
}

// ── 面板增删改 ──
function addPanel(type: string) {
  const def = getPanel(type)
  if (!def || !dsl.value) return
  const base = def.defaultDsl()
  const id = 'p-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
  const maxY = dsl.value.panels.reduce((m, p) => Math.max(m, p.layout.y + p.layout.h), 0)
  const panel: PanelDsl = {
    id,
    type,
    title: def.name,
    showTitle: true,
    dataset: null,
    fieldMapping: base.fieldMapping || {},
    options: base.options || {},
    style: {},
    refresh: { enabled: false, interval: 60 },
    layout: { x: 0, y: maxY, w: base.layout?.w || 8, h: base.layout?.h || 6 },
  }
  dsl.value.panels.push(panel)
  selectedId.value = id
  commit()
}

function removePanel(id: string) {
  if (!dsl.value) return
  dsl.value.panels = dsl.value.panels.filter((p) => p.id !== id)
  if (selectedId.value === id) selectedId.value = null
  commit()
}

function setByPath(obj: Record<string, unknown>, path: string, value: unknown) {
  const keys = path.split('.')
  let cur: Record<string, unknown> = obj
  for (let i = 0; i < keys.length - 1; i++) {
    const k = keys[i] as string
    if (cur[k] == null || typeof cur[k] !== 'object') cur[k] = {}
    cur = cur[k] as Record<string, unknown>
  }
  const last = keys[keys.length - 1] as string
  if (value === undefined || value === '') {
    delete cur[last]
  } else {
    cur[last] = value
  }
}

function onUpdatePanel(path: string, value: unknown) {
  if (!selectedPanel.value) return
  setByPath(selectedPanel.value as unknown as Record<string, unknown>, path, value)
  commitDebounced()
}

// ── 键盘删除守卫：输入聚焦时不触发 ──
function onKeyDown(e: KeyboardEvent) {
  if (e.key !== 'Delete' && e.key !== 'Backspace') return
  const el = document.activeElement as HTMLElement | null
  const tag = el?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el?.isContentEditable) return
  if (!selectedId.value) return
  e.preventDefault()
  removePanel(selectedId.value)
}

// ── 数据加载与持久化 ──
async function loadPortal() {
  const resp = await dashboardPortal()
  const portal = resp.data.data
  dashboardId.value = portal.dashboardId
  dsl.value = normalizeDsl(portal.config)
  filterValues.value = initFilterValues(dsl.value.filters)
  selectedId.value = null
  resetHistory()
}

function normalizeDsl(config: DashboardDsl | null): DashboardDsl {
  const base: DashboardDsl = {
    version: 1,
    grid: { cols: 24, rowHeight: 40, margin: [12, 12], responsive: true },
    refresh: { enabled: false, interval: 60 },
    filters: [],
    panels: [],
  }
  if (!config) return base
  return {
    version: config.version || 1,
    grid: config.grid || base.grid,
    refresh: config.refresh || base.refresh,
    filters: config.filters || [],
    panels: config.panels || [],
  }
}

async function loadDatasets() {
  const resp = await datasetList({ enabled: true })
  datasets.value = resp.data.data || []
}

async function save() {
  if (!dashboardId.value || !dsl.value) return
  saving.value = true
  try {
    await dashboardSavePersonal(dashboardId.value, dsl.value)
    message.success('已保存')
  } finally {
    saving.value = false
  }
}

async function resetDefault() {
  if (!dashboardId.value) return
  await dashboardResetPersonal(dashboardId.value)
  await loadPortal()
  message.success('已恢复默认')
}

function goDatasets() {
  datasetPanelOpen.value = true
}

function goPortal() {
  router.push({ name: RouteNames.DASHBOARD })
}

onMounted(() => {
  loadPortal()
  loadDatasets()
  document.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeyDown)
})
</script>

<template>
  <div class="designer">
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <a-button type="text" @click="goPortal">
          <template #icon><ArrowLeftOutlined /></template>
          返回
        </a-button>
      </div>
      <div class="toolbar-right">
        <a-tooltip title="撤销">
          <a-button type="text" :disabled="!canUndo" @click="undo">
            <template #icon><UndoOutlined /></template>
          </a-button>
        </a-tooltip>
        <a-tooltip title="重做">
          <a-button type="text" :disabled="!canRedo" @click="redo">
            <template #icon><RedoOutlined /></template>
          </a-button>
        </a-tooltip>
        <span class="toolbar-divider" />
        <a-button @click="filterConfigOpen = true">
          <template #icon><FilterOutlined /></template>
          筛选器
        </a-button>
        <a-button @click="goDatasets">
          <template #icon><UnorderedListOutlined /></template>
          数据集
        </a-button>
        <a-popconfirm title="恢复为租户默认模板？当前个人修改将丢失" @confirm="resetDefault">
          <a-button>
            <template #icon><ReloadOutlined /></template>
            恢复默认
          </a-button>
        </a-popconfirm>
        <a-button type="primary" :loading="saving" @click="save">
          <template #icon><SaveOutlined /></template>
          保存
        </a-button>
      </div>
    </div>

    <div class="designer-body">
      <main
        class="designer-canvas"
        :style="{ paddingLeft: (libraryOpen ? 232 : 20) + 'px', paddingRight: (rightWidth + 12) + 'px' }"
        @click.self="selectedId = null"
      >
        <FilterBar v-model="filterValues" :filters="filters" class="designer-filter" />
        <DashboardCanvas
          v-if="dsl"
          :dsl="dsl"
          :selected-id="selectedId"
          :global-params="globalParams"
          @select="selectedId = $event"
          @remove="removePanel"
          @change="commitDebounced"
        />
      </main>

      <!-- 左侧悬浮面板库 -->
      <Transition name="lib-slide">
        <aside v-show="libraryOpen" class="floating-panel left">
          <div class="floating-body">
            <PanelLibrary @add="addPanel" />
          </div>
          <!-- 右侧中间收缩手柄：hover 显示向左折叠箭头 -->
          <div class="collapse-handle" title="收起面板库" @click="libraryOpen = false">
            <span class="collapse-arrow"><LeftOutlined /></span>
          </div>
        </aside>
      </Transition>

      <!-- 收起后：左缘展开触发器 -->
      <Transition name="expand-fade">
        <div v-show="!libraryOpen" class="expand-handle" title="展开面板库" @click="libraryOpen = true">
          <RightOutlined />
        </div>
      </Transition>

      <!-- 右侧悬浮面板（可拖拽调宽）：数据集面板与配置面板二选一 -->
      <aside class="floating-panel right" :class="{ dragging }" :style="{ width: rightWidth + 'px' }">
        <div class="resize-handle" @mousedown.prevent="beginResize" />
        <div class="floating-body">
          <DatasetPanel
            v-if="datasetPanelOpen"
            @close="datasetPanelOpen = false"
            @changed="loadDatasets"
          />
          <PanelConfigDrawer
            v-else
            :panel="selectedPanel"
            :datasets="datasets"
            @update="onUpdatePanel"
          />
        </div>
      </aside>

      <!-- 右下角可拖拽数据集帮助悬浮球 -->
      <DatasetHelp />
    </div>

    <FilterConfigModal v-model:open="filterConfigOpen" :filters="filters" @save="onSaveFilters" />
  </div>
</template>

<style scoped lang="scss">
.designer {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f7f8fa;
}

.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 0;
  background: #f7f8fa;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-divider {
  width: 1px;
  height: 18px;
  background: #e5e6eb;
  margin: 0 4px;
}

.designer-body {
  position: relative;
  flex: 1;
  min-height: 0;
}

.designer-canvas {
  height: 100%;
  overflow: auto;
  padding: 0;
  background: #f7f8fa;
  transition: padding 0.28s ease;
}

.designer-filter {
  margin: 12px 12px 0;
}

/* 悬浮卡片：圆角、边框、微阴影 */
.floating-panel {
  position: absolute;
  top: 12px;
  bottom: 12px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #eceef1;
  border-radius: 10px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

.floating-panel.left {
  left: 12px;
  width: 220px;
}

.floating-panel.right {
  right: 12px;
  min-width: 300px;
}

.floating-panel.right.dragging {
  user-select: none;
}

.floating-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.collapse-handle {
  position: absolute;
  right: -7px;
  top: 0;
  bottom: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  cursor: pointer;
}

.collapse-handle::after {
  content: '';
  position: absolute;
  right: 2px;
  width: 3px;
  height: 40px;
  border-radius: 1px;
  background: #d9d9d9;
  transition: opacity 0.2s ease;
}

.collapse-arrow {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 40px;
  border: 1px solid #eceef1;
  border-radius: 6px;
  background: #fff;
  color: #8c8c8c;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.collapse-handle:hover::after {
  opacity: 0;
}

.collapse-handle:hover .collapse-arrow {
  opacity: 1;
}

.expand-handle {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 46px;
  border: 1px solid #eceef1;
  border-left: none;
  border-radius: 0 8px 8px 0;
  background: #fff;
  color: #8c8c8c;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

/* 面板库滑入滑出与展开手柄淡入淡出过渡 */
.lib-slide-enter-active,
.lib-slide-leave-active {
  transition: transform 0.28s ease, opacity 0.28s ease;
}

.lib-slide-enter-from,
.lib-slide-leave-to {
  transform: translateX(-16px);
  opacity: 0;
}

.expand-fade-enter-active,
.expand-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.expand-fade-enter-from,
.expand-fade-leave-to {
  opacity: 0;
  transform: translateX(-8px) translateY(-50%);
}

/* 右侧拖拽手柄（参考 WorkflowConfigPanel） */
.resize-handle {
  position: absolute;
  left: -5px;
  top: 0;
  bottom: 0;
  width: 3px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s ease;
}

.resize-handle::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 40px;
  border-radius: 1px;
  background: #c9c9c9;
  transition: background 0.2s ease;
}

.resize-handle:hover::after,
.dragging .resize-handle::after {
  display: none;
}

.resize-handle:hover,
.dragging .resize-handle {
  background: #1677ff;
}
</style>

<script setup lang="ts">
/**
 * 工作台设计器：三区式布局（面板库 / 画布 / 配置），支持拖拽、撤销重做、样式覆盖、保存个人副本。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { message } from 'ant-design-vue'
import { debounce } from 'lodash-es'
import {
  ArrowLeftOutlined,
  LeftOutlined,
  RightOutlined,
  RedoOutlined,
  HistoryOutlined,
  LayoutOutlined,
  ClearOutlined,
  SaveOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons-vue'
import { registerBuiltinPanels, getPanel } from '@/components/dashboard/panels'
import DashboardCanvas from '@/components/dashboard/DashboardCanvas.vue'
import PanelLibrary from '@/components/dashboard/PanelLibrary.vue'
import PanelConfigDrawer from '@/components/dashboard/PanelConfigDrawer.vue'
import DatasetPanel from '@/components/dashboard/DatasetPanel.vue'
import DatasetHelp from '@/components/dashboard/DatasetHelp.vue'
import SaveVersionModal from '@/components/dashboard/SaveVersionModal.vue'
import HistoryDrawer from '@/components/dashboard/HistoryDrawer.vue'
import AutoLayoutModal from '@/components/dashboard/AutoLayoutModal.vue'
import { applyAutoLayout, type AutoLayoutMode } from '@/components/dashboard/autoLayout'
import {
  dashboardPortal,
  dashboardSavePersonal,
  dashboardSaveVersion,
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

// 栅格分辨率：目标细粒度(48列/20行高，比基准 24/40 细 2 倍)；基准用于面板 defaultDsl 缩放与存量迁移
const GRID_TARGET = { cols: 48, rowHeight: 20, margin: [12, 12] as [number, number], responsive: true }
const AUTHOR_COLS = 24
const AUTHOR_ROW_HEIGHT = 40

// ── 悬浮面板：左侧可隐藏，右侧可拖拽调宽 ──
const RIGHT_MIN = 310
const libraryOpen = ref(true)
const datasetPanelOpen = ref(false)
const rightWidth = ref(RIGHT_MIN)
const dragging = ref(false)
const maxRightWidth = computed(() => Math.max(RIGHT_MIN, Math.floor(window.innerWidth * 0.45)))

// 数据集面板停靠在配置面板左侧（固定宽度）
const DATASET_WIDTH = 380
const canvasPaddingRight = computed(() =>
  datasetPanelOpen.value ? rightWidth.value + DATASET_WIDTH + 12 : rightWidth.value + 5,
)

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

// ── 未保存离开拦截 ──
// 以已保存快照为基线，当前 dsl 序列化与其不同即为脏
const savedSnapshot = ref('')
const leaveModalOpen = ref(false)
let pendingLeave: (() => void) | null = null

function markSaved() {
  savedSnapshot.value = dsl.value ? JSON.stringify(dsl.value) : ''
}

const isDirty = computed(
  () => !!dsl.value && JSON.stringify(dsl.value) !== savedSnapshot.value,
)

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
  // 面板 defaultDsl 的 w/h 按基准分辨率(24/40)编写，按当前栅格等比缩放以保持视觉尺寸
  const g = dsl.value.grid
  const wScale = (g?.cols || GRID_TARGET.cols) / AUTHOR_COLS
  const hScale = AUTHOR_ROW_HEIGHT / (g?.rowHeight || GRID_TARGET.rowHeight)
  const panel: PanelDsl = {
    id,
    type,
    title: def.name,
    showTitle: true,
    showHeader: true,
    dataset: null,
    fieldMapping: base.fieldMapping || {},
    options: base.options || {},
    style: {},
    refresh: { enabled: false, interval: 60 },
    layout: {
      x: 0,
      y: maxY,
      w: Math.max(1, Math.round((base.layout?.w || 8) * wScale)),
      h: Math.max(1, Math.round((base.layout?.h || 6) * hScale)),
    },
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
  selectedId.value = null
  resetHistory()
  markSaved()
}

function normalizeDsl(config: DashboardDsl | null): DashboardDsl {
  const base: DashboardDsl = {
    version: 1,
    grid: { ...GRID_TARGET },
    refresh: { enabled: false, interval: 60 },
    panels: [],
  }
  if (!config) return base
  const grid = config.grid || { ...GRID_TARGET }
  let panels = config.panels || []
  // 细粒度栅格迁移：存量分辨率与目标不一致时等比缩放面板坐标，保持视觉布局不变
  if (grid.cols !== GRID_TARGET.cols || grid.rowHeight !== GRID_TARGET.rowHeight) {
    const ws = GRID_TARGET.cols / (grid.cols || GRID_TARGET.cols)
    const hs = (grid.rowHeight || GRID_TARGET.rowHeight) / GRID_TARGET.rowHeight
    panels = panels.map((p) => ({
      ...p,
      layout: {
        x: Math.round(p.layout.x * ws),
        y: Math.round(p.layout.y * hs),
        w: Math.max(1, Math.round(p.layout.w * ws)),
        h: Math.max(1, Math.round(p.layout.h * hs)),
      },
    }))
  }
  return {
    version: config.version || 1,
    grid: { ...grid, cols: GRID_TARGET.cols, rowHeight: GRID_TARGET.rowHeight },
    refresh: config.refresh || base.refresh,
    panels,
  }
}

async function loadDatasets() {
  const resp = await datasetList({ enabled: true })
  datasets.value = resp.data.data || []
}

// 保存模态与历史版本
const saveModalOpen = ref(false)
const historyOpen = ref(false)
// 自动排版：重排后需强制重建画布(改变存量面板坐标，新增/删除以外不会触发内部 layout 重建)
const autoLayoutOpen = ref(false)
const canvasKey = ref(0)
// 防碰撞开关（默认开启：面板只能拖到空位）
const preventCollision = ref(true)

function onAutoLayout(mode: AutoLayoutMode) {
  if (!dsl.value) return
  applyAutoLayout(dsl.value.panels, dsl.value.grid?.cols || 48, mode)
  selectedId.value = null
  canvasKey.value++
  commit()
  message.success('已自动排版')
}

/** 一键清空所有面板（可撤销） */
function clearAll() {
  if (!dsl.value) return
  dsl.value.panels = []
  selectedId.value = null
  canvasKey.value++
  commit()
  message.success('已清空')
}

async function doDirectSave() {
  if (!dashboardId.value || !dsl.value) return
  saving.value = true
  try {
    await dashboardSavePersonal(dashboardId.value, dsl.value)
    markSaved()
    saveModalOpen.value = false
    message.success('已保存')
  } finally {
    saving.value = false
  }
}

async function doSaveVersion(note: string) {
  if (!dashboardId.value || !dsl.value) return
  saving.value = true
  try {
    await dashboardSaveVersion(dashboardId.value, dsl.value, note)
    markSaved()
    saveModalOpen.value = false
    message.success('已保存为历史版本')
  } finally {
    saving.value = false
  }
}

/** 回滚后重载画布（后端已落地当前配置） */
function onRolledback(config: DashboardDsl) {
  dsl.value = normalizeDsl(config)
  selectedId.value = null
  canvasKey.value++
  resetHistory()
  markSaved()
}

function goDatasets() {
  datasetPanelOpen.value = true
}

function goPortal() {
  router.push({ name: RouteNames.DASHBOARD })
}

// 路由离开守卫：脏时拦截、弹模态由用户决定（返回按钮/后退/任意跳转统一命中）
onBeforeRouteLeave(() => {
  if (!isDirty.value) return true
  return new Promise<boolean>((resolve) => {
    pendingLeave = () => resolve(true)
    leaveModalOpen.value = true
    // 模态关闭（取消/点 X）而未确认离开时，解析 false 留在页面
    leaveResolve = resolve
  })
})

let leaveResolve: ((v: boolean) => void) | null = null

/** 直接退出：不保存，放行导航 */
function leaveWithoutSave() {
  leaveModalOpen.value = false
  const go = pendingLeave
  pendingLeave = null
  leaveResolve = null
  if (go) go()
}

/** 保存后退出 */
async function saveThenLeave() {
  await doDirectSave()
  leaveWithoutSave()
}

/** 取消离开：留在页面 */
function cancelLeave() {
  leaveModalOpen.value = false
  pendingLeave = null
  if (leaveResolve) leaveResolve(false)
  leaveResolve = null
}

/** 关闭标签页/刷新的兑底提醒（浏览器原生，无法自定义按钮） */
function onBeforeUnload(e: BeforeUnloadEvent) {
  if (isDirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onMounted(() => {
  loadPortal()
  loadDatasets()
  document.addEventListener('keydown', onKeyDown)
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('beforeunload', onBeforeUnload)
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
        <a-button type="text" @click="autoLayoutOpen = true">
          <template #icon><LayoutOutlined /></template>
          自动排版
        </a-button>
        <a-popconfirm title="清空当前所有面板？可撤销" ok-text="清空" @confirm="clearAll">
          <a-button type="text" danger>
            <template #icon><ClearOutlined /></template>
            清空
          </a-button>
        </a-popconfirm>
      </div>
      <div class="toolbar-right">
        <a-tooltip title="开启后面板只能拖到空位；关闭后可拖到任意位置、其他面板自动归位">
          <span class="toolbar-toggle">
            <span class="toggle-label">防碰撞</span>
            <a-switch v-model:checked="preventCollision" size="small" />
          </span>
        </a-tooltip>
        <span class="toolbar-divider" />
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
        <a-button @click="goDatasets">
          <template #icon><UnorderedListOutlined /></template>
          数据集
        </a-button>
        <a-button @click="historyOpen = true">
          <template #icon><HistoryOutlined /></template>
          历史版本
        </a-button>
        <a-button type="primary" :loading="saving" @click="saveModalOpen = true">
          <template #icon><SaveOutlined /></template>
          保存
        </a-button>
      </div>
    </div>

    <div class="designer-body">
      <main
        class="designer-canvas"
        :style="{ paddingLeft: (libraryOpen ? 232 : 0) + 'px', paddingRight: canvasPaddingRight + 'px' }"
        @click.self="selectedId = null"
      >
        <DashboardCanvas
          v-if="dsl"
          :key="canvasKey"
          :dsl="dsl"
          :selected-id="selectedId"
          :prevent-collision="preventCollision"
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

      <!-- 右侧悬浮面板配置（可拖拽调宽） -->
      <aside class="floating-panel right" :class="{ dragging }" :style="{ width: rightWidth + 'px' }">
        <div class="resize-handle" @mousedown.prevent="beginResize" />
        <div class="floating-body">
          <PanelConfigDrawer
            :panel="selectedPanel"
            :datasets="datasets"
            @update="onUpdatePanel"
          />
        </div>
      </aside>

      <!-- 数据集面板：停靠在配置面板左侧 -->
      <Transition name="lib-slide">
        <aside
          v-show="datasetPanelOpen"
          class="floating-panel dataset"
          :style="{ right: (rightWidth + 20) + 'px', width: DATASET_WIDTH + 'px' }"
        >
          <div class="floating-body">
            <DatasetPanel @close="datasetPanelOpen = false" @changed="loadDatasets" />
          </div>
        </aside>
      </Transition>

      <!-- 右下角可拖拽数据集帮助悬浮球 -->
      <DatasetHelp />
    </div>

    <SaveVersionModal
      v-model:open="saveModalOpen"
      :saving="saving"
      @save="doDirectSave"
      @save-version="doSaveVersion"
    />
    <HistoryDrawer
      v-model:open="historyOpen"
      :dashboard-id="dashboardId"
      @rolledback="onRolledback"
    />
    <AutoLayoutModal v-model:open="autoLayoutOpen" @apply="onAutoLayout" />

    <a-modal
      :open="leaveModalOpen"
      title="有未保存的修改"
      :footer="null"
      :mask-closable="false"
      width="420px"
      @cancel="cancelLeave"
    >
      <p class="leave-tip">当前工作台存在未保存的修改，直接退出将丢失这些更改。</p>
      <div class="leave-actions">
        <a-button @click="cancelLeave">取消</a-button>
        <a-button @click="leaveWithoutSave">直接退出</a-button>
        <a-button type="primary" :loading="saving" @click="saveThenLeave">保存后退出</a-button>
      </div>
    </a-modal>
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

.toolbar-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.toggle-label {
  font-size: 13px;
  color: #595959;
}

.leave-tip {
  margin: 0 0 20px;
  font-size: 14px;
  color: #595959;
}

.leave-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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

.floating-panel.dataset {
  z-index: 9;
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

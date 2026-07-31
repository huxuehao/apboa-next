<script setup lang="ts">
/**
 * 面板渲染器：按注册表分发面板组件，负责取数、定时刷新、样式覆盖、
 * 标题栏(showHeader)/标题文本(showTitle)显隐，以及面板私有筛选器（p_ 作用域）。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getPanel } from './panels'
import { useDatasetData } from './composables/useDatasetData'
import { useRefreshTimer } from './composables/useRefreshTimer'
import FilterBar from './filter/FilterBar.vue'
import { resolveIcon } from './icons/iconRegistry'
import { getPanelActions, getPanelRefresh, unregisterPanelActions } from './panelActionsStore'
import {
  buildFilterParams,
  initFilterValues,
  type FilterValues,
} from './filter/filterParams'
import type { PanelDsl, RefreshConfig } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  globalRefresh?: RefreshConfig
  interactive?: boolean
  /** 设计器中是否选中（仅传给需要编辑态预览的面板，如快捷方式） */
  selected?: boolean
}>()

// 允许覆盖的样式白名单，禁用渐变背景等违规样式
const definition = computed(() => getPanel(props.panel.type))
const { result, loading, error, load } = useDatasetData()

const style = computed(() => props.panel.style || {})

// 卡片容器样式：背景/边框/圆角/阴影（作用于 .panel-card）
const cardStyle = computed(() => {
  const s = style.value
  const out: Record<string, string> = {}
  if (s.backgroundColor) out.backgroundColor = s.backgroundColor
  if (s.borderRadius) out.borderRadius = withPx(s.borderRadius)
  if (s.boxShadow) out.boxShadow = s.boxShadow
  // 边框：none 关闭，否则可自定义边框色
  if (s.border === 'none') {
    out.border = 'none'
  } else if (s.borderColor) {
    out.border = `1px solid ${s.borderColor}`
  }
  return out
})

// 标题栏样式：背景/标题色/分割线（作用于 .panel-header）
const headerStyle = computed(() => {
  const s = style.value
  const out: Record<string, string> = {}
  if (s.headerBg) out.backgroundColor = s.headerBg
  if (s.headerColor) out.color = s.headerColor
  if (s.headerDivider === 'none') out.borderBottom = 'none'
  return out
})

// 内容区样式：四边内边距 + 文字类。文字色/字号/字重编译为 CSS 变量（由各面板主文字元素消费），
// 避免依赖 CSS 继承被子元素硬编码样式覆盖；textAlign 对块级文本按继承生效
const bodyStyle = computed(() => {
  const s = style.value
  const out: Record<string, string> = {}
  out.padding = resolvePadding(s)
  if (s.color) out['--dash-text-color'] = s.color
  if (s.fontSize) out['--dash-text-size'] = withPx(s.fontSize)
  if (s.fontWeight) out['--dash-text-weight'] = s.fontWeight
  if (s.textAlign) out.textAlign = s.textAlign
  return out
})

// 四边内边距：paddingOff 关闭时为 0；否则上下默认 12、左右默认 14（px）
function resolvePadding(s: Record<string, string>): string {
  if (s.paddingOff === 'true') return '0'
  const top = s.padTop ?? '12'
  const right = s.padRight ?? '14'
  const bottom = s.padBottom ?? '12'
  const left = s.padLeft ?? '14'
  return `${top}px ${right}px ${bottom}px ${left}px`
}

// 纯数字补充 px 单位；已带单位（如 8px）则原样返回，兼容旧值
function withPx(v: string): string {
  return /^\d+(\.\d+)?$/.test(v.trim()) ? `${v}px` : v
}

// ── 私有筛选器（面板作用域，编译为 p_ 前缀参数，仅注入自身取数请求） ──
const panelFilter = computed(() => props.panel.panelFilter)
const panelFilterActive = computed(
  () =>
    definition.value?.dataRequirement.supportsPanelFilters === true &&
    panelFilter.value?.enabled === true &&
    (panelFilter.value?.items?.length || 0) > 0,
)
const pfItems = computed(() => panelFilter.value?.items || [])
const pfSize = computed(() => panelFilter.value?.size || 'small')
const pfShowLabel = computed(() => panelFilter.value?.showLabel !== false)

const panelFilterValues = ref<FilterValues>({})
watch(
  () => panelFilter.value?.items,
  (items) => {
    panelFilterValues.value = initFilterValues(items || [])
  },
  { immediate: true, deep: true },
)

const panelFilterParams = computed(() =>
  panelFilterActive.value
    ? buildFilterParams(pfItems.value, panelFilterValues.value)
    : {},
)

// ── 标题栏/标题文本显隐与筛选器位置 ──
const titleTextVisible = computed(() => props.panel.showTitle !== false && !!props.panel.title)
const titleIconComp = computed(() => resolveIcon(props.panel.titleIcon))
const headerAllowed = computed(() => props.panel.showHeader !== false)
// 归一化位置（兼容旧值 contentTop/contentBottom → 左对齐）；标题栏隐藏时 header 回退到内容区左上
const effectivePosition = computed(() => {
  const raw = (panelFilter.value?.position as string) || 'header'
  const pos =
    raw === 'contentTop' ? 'contentTopLeft' : raw === 'contentBottom' ? 'contentBottomLeft' : raw
  return pos === 'header' && !headerAllowed.value ? 'contentTopLeft' : pos
})
const headerFilterVisible = computed(
  () => panelFilterActive.value && effectivePosition.value === 'header',
)
const headerVisible = computed(
  () => headerAllowed.value && (titleTextVisible.value || headerFilterVisible.value),
)
const contentTopFilterVisible = computed(
  () => panelFilterActive.value && effectivePosition.value.startsWith('contentTop'),
)
const contentBottomFilterVisible = computed(
  () => panelFilterActive.value && effectivePosition.value.startsWith('contentBottom'),
)
const contentFilterAlignRight = computed(() => effectivePosition.value.endsWith('Right'))

const refreshConfig = computed(() => {
  const r = props.panel.refresh || props.globalRefresh
  return { enabled: !!r?.enabled, interval: r?.interval || 0 }
})

function reload(silent = false) {
  // 只要绑定了数据集就取数（needsDataset 仅用于配置提示，不决定是否加载）
  const dataset = props.panel.dataset
  if (dataset?.id) {
    // 数据集固定参数 + 面板私有筛选参数（系统参数由后端注入且不可覆盖）
    load(
      dataset.id,
      {
        ...(dataset.params || {}),
        ...panelFilterParams.value,
      },
      undefined,
      silent,
    )
    return
  }
  // 无数据集面板（如自定义组件）：定时刷新转发给组件自身暴露的 refresh 处理器
  getPanelRefresh(props.panel.id)?.()
}

// ── 标题栏操作按钮（通用机制：面板组件运行时注册，按配置显隐） ──
const panelActions = computed(() => {
  const visibility = (props.panel.options?.actionVisibility as Record<string, boolean>) || {}
  return getPanelActions(props.panel.id).filter((a) => visibility[a.key] !== false)
})

onBeforeUnmount(() => unregisterPanelActions(props.panel.id))

onMounted(() => reload())
watch(() => props.panel.dataset, () => reload(), { deep: true })
watch(panelFilterParams, () => reload())
// 定时刷新走静默模式：不置 loading、不弹遮罩，旧数据保留至新数据到达，避免周期性闪烁
useRefreshTimer(() => reload(true), () => refreshConfig.value)
</script>

<template>
  <div class="panel-card" :style="cardStyle">
    <div v-if="headerVisible" class="panel-header" :style="headerStyle">
      <span v-if="titleTextVisible" class="panel-title">
        <component :is="titleIconComp" v-if="titleIconComp" class="panel-title-icon" />
        <span class="panel-title-text">{{ panel.title }}</span>
      </span>
      <span v-if="panelActions.length" class="panel-actions">
        <a-tooltip v-for="a in panelActions" :key="a.key" :title="a.label">
          <a-button type="text" size="small" class="panel-action-btn" @click.stop="a.run()">
            <component :is="resolveIcon(a.icon)" v-if="a.icon && resolveIcon(a.icon)" />
            <template v-else>{{ a.label }}</template>
          </a-button>
        </a-tooltip>
      </span>
      <FilterBar
        v-if="headerFilterVisible"
        v-model="panelFilterValues"
        :filters="pfItems"
        variant="plain"
        :size="pfSize"
        :show-label="pfShowLabel"
        class="header-filter"
      />
    </div>
    <div class="panel-body" :style="bodyStyle">
      <FilterBar
        v-if="contentTopFilterVisible"
        v-model="panelFilterValues"
        :filters="pfItems"
        variant="plain"
        :size="pfSize"
        :show-label="pfShowLabel"
        class="content-filter"
        :class="{ 'align-right': contentFilterAlignRight }"
      />
      <div class="panel-content">
        <component
          :is="definition.component"
          v-if="definition"
          :panel="panel"
          :data="result"
          :loading="loading"
          :error="error"
          :interactive="interactive"
          :selected="selected"
        />
        <div v-else class="panel-unknown">未知面板类型：{{ panel.type }}</div>
      </div>
      <FilterBar
        v-if="contentBottomFilterVisible"
        v-model="panelFilterValues"
        :filters="pfItems"
        variant="plain"
        :size="pfSize"
        :show-label="pfShowLabel"
        class="content-filter"
        :class="{ 'align-right': contentFilterAlignRight }"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.panel-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.panel-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  color: #1a1a1a;
  border-bottom: 1px solid #f0f0f0;
  min-height: 40px;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: inherit;
}

.panel-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  /* 靠右显示；若同时存在标题栏筛选器，按钮位于其左侧 */
  margin-left: auto;
}

.panel-action-btn {
  color: #8c8c8c;
}

.panel-action-btn:hover {
  color: #1677ff;
}

.panel-title-icon {
  flex-shrink: 0;
}

.panel-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-filter {
  margin-left: auto;
  flex-shrink: 1;
  min-width: 0;
  justify-content: flex-end;
}

.panel-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  overflow: hidden;
}

.content-filter {
  flex-shrink: 0;
}

.content-filter.align-right {
  justify-content: flex-end;
}

.panel-content {
  flex: 1;
  min-height: 0;
}

.panel-unknown {
  font-size: 13px;
  color: #999;
}
</style>

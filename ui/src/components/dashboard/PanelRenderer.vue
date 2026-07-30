<script setup lang="ts">
/**
 * 面板渲染器：按注册表分发面板组件，负责取数、定时刷新与样式覆盖。
 *
 * @author huxuehao
 */
import { computed, onMounted, watch } from 'vue'
import { getPanel } from './panels'
import { useDatasetData } from './composables/useDatasetData'
import { useRefreshTimer } from './composables/useRefreshTimer'
import type { PanelDsl, RefreshConfig } from '@/types/dashboard'

const props = defineProps<{
  panel: PanelDsl
  globalRefresh?: RefreshConfig
  globalParams?: Record<string, unknown>
  interactive?: boolean
}>()

// 允许覆盖的样式白名单，禁用渐变背景等违规样式
const STYLE_WHITELIST = [
  'backgroundColor',
  'color',
  'border',
  'borderColor',
  'borderRadius',
  'padding',
  'fontSize',
  'fontWeight',
  'textAlign',
]

const definition = computed(() => getPanel(props.panel.type))
const { result, loading, error, load } = useDatasetData()

const bodyStyle = computed(() => {
  const style = props.panel.style || {}
  const out: Record<string, string> = {}
  for (const key of STYLE_WHITELIST) {
    if (style[key] != null && style[key] !== '') {
      out[key] = String(style[key])
    }
  }
  return out
})

const refreshConfig = computed(() => {
  const r = props.panel.refresh || props.globalRefresh
  return { enabled: !!r?.enabled, interval: r?.interval || 0 }
})

function reload() {
  // 只要绑定了数据集就取数（needsDataset 仅用于配置提示，不决定是否加载）
  const dataset = props.panel.dataset
  if (dataset?.id) {
    // 面板自身参数与全局筛选参数合并（全局优先）
    load(dataset.id, { ...(dataset.params || {}), ...(props.globalParams || {}) })
  }
}

onMounted(reload)
watch(() => props.panel.dataset, reload, { deep: true })
watch(() => props.globalParams, reload, { deep: true })
useRefreshTimer(reload, () => refreshConfig.value)
</script>

<template>
  <div class="panel-card">
   <div v-if="panel.showTitle !== false && panel.title" class="panel-header">{{ panel.title }}</div>
    <div class="panel-body" :style="bodyStyle">
      <component
        :is="definition.component"
        v-if="definition"
        :panel="panel"
        :data="result"
        :loading="loading"
        :error="error"
        :interactive="interactive"
      />
      <div v-else class="panel-unknown">未知面板类型：{{ panel.type }}</div>
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
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  border-bottom: 1px solid #f0f0f0;
}

.panel-body {
  flex: 1;
  min-height: 0;
  padding: 12px 14px;
  overflow: hidden;
}

.panel-unknown {
  font-size: 13px;
  color: #999;
}
</style>

<script setup lang="ts">
/**
 * 自定义组件面板：渲染 portal 目录下的业务组件（编译期路径隔离）。
 * 异步加载 + 运行时错误兜底；透传用户 props JSON 与 panelContext；
 * 自动识别组件暴露的 panelActions / refresh 并注册到通用操作按钮机制。
 *
 * @author huxuehao
 */
import {
  computed,
  onBeforeUnmount,
  onErrorCaptured,
  ref,
  shallowRef,
  watch,
  type Component,
  type ComponentPublicInstance,
} from 'vue'
import type {
  DatasetExecuteResult,
  PanelAction,
  PanelDsl,
  PanelPropItem,
} from '@/types/dashboard'
import { registerPanelActions, unregisterPanelActions } from '../../panelActionsStore'
import { getPortalLoader } from './portalRegistry'

const props = defineProps<{
  panel: PanelDsl
  data?: DatasetExecuteResult | null
  loading?: boolean
  error?: string | null
  interactive?: boolean
}>()

const componentId = computed(() => (props.panel.options?.component as string) || '')

// ── 异步加载 portal 组件 ──
const status = ref<'idle' | 'loading' | 'ready' | 'failed'>('idle')
const comp = shallowRef<Component | null>(null)
const runtimeError = ref<string | null>(null)

watch(
  componentId,
  async (id) => {
    runtimeError.value = null
    comp.value = null
    if (!id) {
      status.value = 'idle'
      return
    }
    const loader = getPortalLoader(id)
    if (!loader) {
      status.value = 'failed'
      return
    }
    status.value = 'loading'
    try {
      const mod = await loader()
      // 防止加载期间已切换组件
      if (componentId.value === id) {
        comp.value = mod.default
        status.value = 'ready'
      }
    } catch {
      if (componentId.value === id) status.value = 'failed'
    }
  },
  { immediate: true },
)

// ── 用户 props 解析：优先类型化 propsList（按声明类型强制转换），兼容旧版 props JSON 字符串 ──
const parsedProps = computed<{ value: Record<string, unknown>; error: string | null }>(() => {
  const list = props.panel.options?.propsList as PanelPropItem[] | undefined
  if (Array.isArray(list) && list.length) {
    return { value: coercePropsList(list), error: null }
  }
  const raw = (props.panel.options?.props as string) || ''
  if (!raw.trim()) return { value: {}, error: null }
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return { value: parsed, error: null }
    }
    return { value: {}, error: 'props 必须是 JSON 对象' }
  } catch {
    return { value: {}, error: 'props 不是合法的 JSON' }
  }
})
const userProps = computed(() => parsedProps.value.value)
const propsError = computed(() => parsedProps.value.error)

/** 按条目声明的类型强制转换，保证注入组件的数据类型正确；非法条目跳过 */
function coercePropsList(list: PanelPropItem[]): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const it of list) {
    if (!it.key) continue
    switch (it.type) {
      case 'string':
        out[it.key] = String(it.value ?? '')
        break
      case 'number': {
        const n = Number(it.value)
        if (!Number.isNaN(n)) out[it.key] = n
        break
      }
      case 'boolean':
        out[it.key] = it.value === true
        break
      case 'date': {
        const d = new Date(String(it.value ?? ''))
        if (!Number.isNaN(d.getTime())) out[it.key] = d
        break
      }
      case 'array':
      case 'object': {
        try {
          const parsed = JSON.parse(String(it.value ?? ''))
          const isArray = Array.isArray(parsed)
          if (it.type === 'array' ? isArray : parsed && typeof parsed === 'object' && !isArray) {
            out[it.key] = parsed
          }
        } catch {
          // 非法 JSON 跳过（编辑弹窗已做前置校验）
        }
        break
      }
    }
  }
  return out
}

// ── 标准上下文注入 ──
const panelContext = computed(() => ({
  panelId: props.panel.id,
  title: props.panel.title || '',
  interactive: props.interactive === true,
}))

// ── 自动识别组件暴露的 panelActions / refresh 并注册 ──
type ExposedInstance = ComponentPublicInstance & {
  panelActions?: PanelAction[]
  refresh?: () => void
}
const instRef = ref<ExposedInstance | null>(null)

watch(instRef, (inst) => {
  if (!inst) {
    unregisterPanelActions(props.panel.id)
    return
  }
  const actions = Array.isArray(inst.panelActions) ? inst.panelActions : []
  const refresh = typeof inst.refresh === 'function' ? () => inst.refresh?.() : undefined
  if (actions.length || refresh) {
    registerPanelActions(props.panel.id, actions, refresh)
  }
})

onBeforeUnmount(() => unregisterPanelActions(props.panel.id))

// 子组件运行时错误兜底：面板内报错，不影响画布
onErrorCaptured((err) => {
  runtimeError.value = err instanceof Error ? err.message : String(err)
  return false
})
</script>

<template>
  <div class="custom-panel">
    <div v-if="!componentId" class="cp-hint">在配置面板选择要渲染的组件</div>
    <div v-else-if="status === 'loading'" class="cp-hint">组件加载中...</div>
    <div v-else-if="status === 'failed'" class="cp-error">
      组件「{{ componentId }}」加载失败或不存在
    </div>
    <div v-else-if="runtimeError" class="cp-error">组件运行出错：{{ runtimeError }}</div>
    <template v-else-if="comp">
      <div v-if="propsError" class="cp-props-warn">{{ propsError }}</div>
      <component :is="comp" ref="instRef" v-bind="userProps" :panel-context="panelContext" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.custom-panel {
  height: 100%;
  overflow: auto;
}

.cp-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 13px;
  color: #bbb;
}

.cp-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 13px;
  color: #cf1322;
}

.cp-props-warn {
  margin-bottom: 8px;
  font-size: 12px;
  color: #d46b08;
}
</style>

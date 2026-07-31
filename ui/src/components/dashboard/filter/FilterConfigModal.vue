<script setup lang="ts">
/**
 * 筛选器配置弹窗：增删改面板私有筛选器。
 *
 * @author huxuehao
 */
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import Sortable from 'sortablejs'
import { DeleteOutlined, HolderOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { DashboardFilter } from '@/types/dashboard'
import { RESERVED_PARAM_KEYS } from './filterParams'

const props = defineProps<{
  open: boolean
  filters: DashboardFilter[]
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'save', filters: DashboardFilter[]): void
}>()

const openLocal = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const typeOptions = [
  { label: '日期范围', value: 'dateRange' },
  { label: '日期选择', value: 'date' },
  { label: '月份选择', value: 'month' },
  { label: '年份选择', value: 'year' },
  { label: '下拉选择', value: 'select' },
  { label: '文本输入', value: 'text' },
]

const items = ref<DashboardFilter[]>([])

// ── 筛选器拖拽排序（SortableJS，拖拽后重建实例避免 DOM 与响应式数据竞态）──
const listRef = ref<HTMLElement | null>(null)
let sortableInstance: ReturnType<typeof Sortable.create> | null = null

function initSortable() {
  if (!listRef.value || sortableInstance) return
  sortableInstance = Sortable.create(listRef.value, {
    animation: 150,
    handle: '.fc-drag-handle',
    ghostClass: 'fc-sortable-ghost',
    onEnd: (evt: { oldIndex?: number; newIndex?: number }) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
      const next = [...items.value]
      const [moved] = next.splice(oldIndex, 1)
      if (!moved) return
      next.splice(newIndex, 0, moved)
      items.value = next
      destroySortable()
      nextTick(initSortable)
    },
  })
}

function destroySortable() {
  sortableInstance?.destroy()
  sortableInstance = null
}

onUnmounted(destroySortable)

watch(
  () => props.open,
  (v) => {
    if (v) {
      items.value = JSON.parse(JSON.stringify(props.filters || []))
      items.value.forEach((it) => {
        if (!it.options) it.options = []
      })
      nextTick(initSortable)
    } else {
      destroySortable()
    }
  },
  { immediate: true },
)

function add() {
  const id = 'f-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5)
  items.value.push({ id, type: 'text', label: '', paramKey: '', options: [] })
}

function remove(idx: number) {
  items.value.splice(idx, 1)
}

// ── 下拉选项列表编辑 ──
function addOption(it: DashboardFilter) {
  if (!it.options) it.options = []
  it.options.push({ label: '', value: '' })
}

function removeOption(it: DashboardFilter, idx: number) {
  it.options?.splice(idx, 1)
}

const PARAM_KEY_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/

/** 校验全部筛选项，返回错误消息（null 表示通过） */
function validateItems(): string | null {
  const seen = new Set<string>()
  for (const it of items.value) {
    if (!it.label || !it.paramKey) {
      return '每个筛选器都需填写显示名称与参数名'
    }
    if (!PARAM_KEY_PATTERN.test(it.paramKey)) {
      return `参数名 ${it.paramKey} 非法：需以字母开头，仅含字母/数字/下划线`
    }
    if (RESERVED_PARAM_KEYS.includes(it.paramKey)) {
      return `参数名 ${it.paramKey} 为系统保留名`
    }
    if (seen.has(it.paramKey)) {
      return `参数名 ${it.paramKey} 重复`
    }
    seen.add(it.paramKey)
    if (it.type === 'select') {
      const opts = (it.options || []).filter((o) => o.label || o.value)
      if (!opts.length) {
        return `下拉筛选器「${it.label}」至少需要一个选项`
      }
      if (opts.some((o) => !o.label || !o.value)) {
        return `下拉筛选器「${it.label}」的选项需同时填写显示名与值`
      }
    }
  }
  return null
}

function onOk() {
  const error = validateItems()
  if (error) {
    message.warning(error)
    return
  }
  for (const it of items.value) {
    it.options = it.type === 'select' ? (it.options || []).filter((o) => o.label && o.value) : []
  }
  emit('save', JSON.parse(JSON.stringify(items.value)))
  openLocal.value = false
}
</script>

<template>
  <a-modal v-model:open="openLocal" title="配置筛选器" width="640px" ok-text="保存" @ok="onOk">
    <p class="fc-hint">
      筛选器改变时联动刷新本面板。参数名会作为数据集命名参数注入：文本/下拉/日期/月份/年份为
      <code>:参数名</code>，日期范围为 <code>:参数名Start</code> 与 <code>:参数名End</code>；
      系统保留 <code>:currentTenantId</code>、<code>:currentUserId</code>，不可占用。
    </p>
    <div class="filter-config">
      <div ref="listRef" class="fc-list">
        <div v-for="(it, idx) in items" :key="it.id" class="fc-row">
          <div class="fc-grid">
            <span class="fc-drag-handle" title="拖拽排序"><HolderOutlined /></span>
            <a-input v-model:value="it.label" placeholder="显示名称" />
            <a-select v-model:value="it.type" :options="typeOptions" class="fc-type" />
            <a-input v-model:value="it.paramKey" placeholder="参数名(英文)" />
            <a-button type="text" danger @click="remove(idx)">
              <template #icon><DeleteOutlined /></template>
            </a-button>
          </div>
          <div v-if="it.type === 'select'" class="fc-options">
            <div class="fc-options-title">下拉选项</div>
            <div v-for="(opt, oi) in it.options" :key="oi" class="fc-option-row">
              <a-input v-model:value="opt.label" placeholder="显示名" />
              <a-input v-model:value="opt.value" placeholder="值" />
              <a-button type="text" danger @click="removeOption(it, oi)">
                <template #icon><DeleteOutlined /></template>
              </a-button>
            </div>
            <a-button type="dashed" size="small" block @click="addOption(it)">
              <template #icon><PlusOutlined /></template>
              添加选项
            </a-button>
          </div>
        </div>
      </div>
      <a-button type="dashed" block @click="add">
        <template #icon><PlusOutlined /></template>
        添加筛选器
      </a-button>
    </div>
  </a-modal>
</template>

<style scoped lang="scss">
.fc-hint {
  margin: 0 0 14px;
  font-size: 12px;
  color: #999;
  line-height: 1.7;

  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: #f2f3f5;
    font-size: 12px;
    color: #c41d7f;
  }
}

.filter-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fc-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fc-list:empty {
  display: none;
}

.fc-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.fc-grid {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 类型选择器固定宽度，保证 4 个汉字完整可见 */
.fc-type {
  flex-shrink: 0;
  width: 112px;
}

.fc-drag-handle {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  color: #bbb;
  cursor: grab;

  &:hover {
    color: #595959;
  }
}

.fc-sortable-ghost {
  opacity: 0.4;
}

.fc-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid #f5f5f5;
  border-radius: 6px;
  background: #fafafa;
}

.fc-options-title {
  font-size: 12px;
  color: #999;
}

.fc-option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

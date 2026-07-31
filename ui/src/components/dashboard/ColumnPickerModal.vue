<script setup lang="ts">
/**
 * 显示列选择弹窗：列渲染为"仿表格列卡片"（表头 + 占位数据行），
 * 点击切换选中（高亮/置灰），整卡拖拽调整显示顺序（选中列带顺序角标）。
 * 列多时自动换行并在区域内滚动，弹窗宽度按列数自适应。
 * 保存空数组表示"显示全部列"（跟随数据集自动展示新列）。
 *
 * @author huxuehao
 */
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import Sortable from 'sortablejs'
import { CheckOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  open: boolean
  /** 数据集全部列名 */
  allColumns: string[]
  /** 已选列（有序）；空数组 = 显示全部 */
  selected: string[]
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'save', columns: string[]): void
}>()

const openLocal = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

interface ColCell {
  name: string
  on: boolean
}

/** 本地草稿：全部列（含选中态），数组顺序即显示顺序 */
const cells = ref<ColCell[]>([])

// ── 整卡拖拽排序（SortableJS，拖后重建避免 DOM 与响应式竞态） ──
// 注意：声明必须位于下方 watch 之前，避免 TDZ 导致组件初始化失败
const gridRef = ref<HTMLElement | null>(null)
let sortableInstance: ReturnType<typeof Sortable.create> | null = null

function initSortable() {
  if (!gridRef.value || sortableInstance) return
  sortableInstance = Sortable.create(gridRef.value, {
    animation: 220,
    ghostClass: 'cc-ghost',
    dragClass: 'cc-dragging',
    onEnd: (evt: { oldIndex?: number; newIndex?: number }) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
      const next = [...cells.value]
      const [moved] = next.splice(oldIndex, 1)
      if (!moved) return
      next.splice(newIndex, 0, moved)
      cells.value = next
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
    if (!v) {
      destroySortable()
      return
    }
    // 顺序 = 已保存顺序在前（选中），其余按数据集顺序附后（未选中）；空配置 = 全选
    const saved = props.selected.filter((name) => props.allColumns.includes(name))
    if (saved.length) {
      const rest = props.allColumns.filter((name) => !saved.includes(name))
      cells.value = [
        ...saved.map((name) => ({ name, on: true })),
        ...rest.map((name) => ({ name, on: false })),
      ]
    } else {
      cells.value = props.allColumns.map((name) => ({ name, on: true }))
    }
    nextTick(initSortable)
  },
)

function toggle(cell: ColCell) {
  cell.on = !cell.on
}

function selectAll() {
  cells.value.forEach((c) => {
    c.on = true
  })
}

function invert() {
  cells.value.forEach((c) => {
    c.on = !c.on
  })
}

function clearAll() {
  cells.value.forEach((c) => {
    c.on = false
  })
}

const pickedCount = computed(() => cells.value.filter((c) => c.on).length)

/** 选中列的显示顺序角标（1 起） */
const orderIndex = computed(() => {
  const map = new Map<string, number>()
  let n = 0
  cells.value.forEach((c) => {
    if (c.on) {
      n += 1
      map.set(c.name, n)
    }
  })
  return map
})

/** 弹窗宽度自适应列数：每卡 116px + 间距，560 ~ 960 之间 */
const modalWidth = computed(() =>
  Math.min(960, Math.max(560, props.allColumns.length * 126 + 56)),
)

function onOk() {
  const picked = cells.value.filter((c) => c.on).map((c) => c.name)
  // 全选且顺序与数据集一致时存空数组，语义为"跟随数据集显示全部"
  const isAllInOrder =
    picked.length === props.allColumns.length &&
    picked.every((name, i) => name === props.allColumns[i])
  emit('save', isAllInOrder ? [] : picked)
  openLocal.value = false
}
</script>

<template>
  <a-modal v-model:open="openLocal" title="选择显示列" :width="modalWidth" ok-text="保存" @ok="onOk">
    <div v-if="!allColumns.length" class="cp-empty">先绑定数据集并加载列后再选择</div>
    <template v-else>
      <div class="cp-toolbar">
        <span class="cp-count">
          已选 {{ pickedCount }} / {{ allColumns.length }}
          <i class="cp-tip">点击选择 · 拖拽排序</i>
        </span>
        <div class="cp-tools">
          <a @click="selectAll">全选</a>
          <a @click="invert">反选</a>
          <a @click="clearAll">清空</a>
        </div>
      </div>

      <div class="cp-grid-wrap">
        <div ref="gridRef" class="cp-grid">
          <div
            v-for="cell in cells"
            :key="cell.name"
            class="col-card"
            :class="{ on: cell.on }"
            @click="toggle(cell)"
          >
            <span v-if="cell.on" class="cc-badge">{{ orderIndex.get(cell.name) }}</span>
            <div class="cc-head">
              <span class="cc-name" :title="cell.name">{{ cell.name }}</span>
              <CheckOutlined v-if="cell.on" class="cc-check" />
            </div>
            <div class="cc-body">
              <span class="cc-line" style="width: 82%" />
              <span class="cc-line" style="width: 58%" />
              <span class="cc-line" style="width: 70%" />
            </div>
          </div>
        </div>
      </div>

      <div v-if="!pickedCount" class="cp-warn">未选择任何列，保存后表格将没有内容</div>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.cp-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.cp-count {
  font-size: 12px;
  color: #999;
}

.cp-tip {
  margin-left: 10px;
  font-style: normal;
  color: #c9ced6;
}

.cp-tools {
  display: flex;
  gap: 14px;
  font-size: 13px;

  a {
    cursor: pointer;
  }
}

/* 列多时换行 + 区域内滚动 */
.cp-grid-wrap {
  max-height: 46vh;
  overflow-y: auto;
  padding: 2px;
}

.cp-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* 仿表格列卡片 */
.col-card {
  position: relative;
  width: 116px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  cursor: grab;
  user-select: none;
  transition: border-color 0.25s ease, opacity 0.25s ease, background 0.25s ease;
}

.col-card:hover {
  border-color: #91b5f0;
}

.col-card.on {
  border-color: #1677ff;
}

/* 表头单元格 */
.cc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 7px 10px;
  background: #f5f6f8;
  border-bottom: 1px solid #eceef1;
  transition: background 0.25s ease, color 0.25s ease;
}

.col-card.on .cc-head {
  background: #1677ff;
  border-bottom-color: #1677ff;
}

.cc-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  transition: color 0.25s ease;
}

.col-card.on .cc-name {
  color: #fff;
}

/* 选中对勾：一次性弹入 */
.cc-check {
  flex-shrink: 0;
  font-size: 11px;
  color: #fff;
  animation: cc-check-in 0.28s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes cc-check-in {
  from {
    transform: scale(0);
  }
  to {
    transform: scale(1);
  }
}

/* 占位数据行：模拟表格内容 */
.cc-body {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 10px;
}

.cc-line {
  height: 6px;
  border-radius: 3px;
  background: #dbe4f2;
  transition: background 0.25s ease;
}

/* 未选中：整列置灰降透明 */
.col-card:not(.on) {
  opacity: 0.55;
  background: #fafafa;
}

.col-card:not(.on) .cc-line {
  background: #e8e8e8;
}

/* 顺序角标：一次性弹入 */
.cc-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #fff;
  font-size: 10px;
  font-weight: 700;
  color: #1677ff;
  animation: cc-check-in 0.28s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* 拖拽中的占位与抓取态 */
.cc-ghost {
  opacity: 0.35;
  border-style: dashed;
  border-color: #1677ff;
}

.cc-dragging {
  cursor: grabbing;
}

.cp-warn {
  margin-top: 10px;
  font-size: 12px;
  color: #d46b08;
}

.cp-empty {
  padding: 8px 0;
  font-size: 12px;
  color: #bbb;
}
</style>

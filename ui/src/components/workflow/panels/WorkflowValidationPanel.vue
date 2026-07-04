<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircleOutlined, CloseOutlined, ExclamationCircleOutlined, WarningOutlined } from '@ant-design/icons-vue'
import type { WorkflowValidationResult } from '@/types/workflow'

type ValidationItem = { nodeId?: string; field?: string; message?: string } | string

const props = defineProps<{
  open: boolean
  result: WorkflowValidationResult | null
  nodeNames: Record<string, string>
}>()

const width = defineModel<number>('width', { default: 442 })

const emit = defineEmits<{
  close: []
  focusNode: [nodeId: string]
}>()

const dragging = ref(false)
const maxWidth = computed(() => Math.floor(window.innerWidth * 0.55))
const errors = computed(() => normalizeItems(props.result?.errors || []))
const warnings = computed(() => normalizeItems(props.result?.warnings || []))
const hasResult = computed(() => Boolean(props.result))

function normalizeItems(items: ValidationItem[]) {
  return items.map((item) => {
    if (typeof item === 'string') {
      return { message: item }
    }
    return item
  })
}

function nodeLabel(nodeId?: string) {
  if (!nodeId) return '工作流'
  return props.nodeNames[nodeId] || nodeId
}

function beginResize(event: MouseEvent) {
  dragging.value = true
  const startX = event.clientX
  const startWidth = width.value
  const onMove = (moveEvent: MouseEvent) => {
    const next = startWidth + (startX - moveEvent.clientX)
    width.value = Math.max(442, Math.min(maxWidth.value, next))
  }
  const onUp = () => {
    dragging.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

watch(
  () => props.open,
  (open) => {
    if (!open) dragging.value = false
  },
)
</script>

<template>
  <aside v-if="open" class="validation-panel" :class="{ dragging }" :style="{ width: `${width}px` }">
    <div class="resize-handle" @mousedown.prevent="beginResize" />

    <header class="validation-header">
      <div>
        <div class="validation-title">
          <CheckCircleOutlined />
          校验结果
        </div>
        <div class="validation-subtitle">
          {{ hasResult ? `${errors.length} 个错误 · ${warnings.length} 个提醒` : '还没有执行校验' }}
        </div>
      </div>
      <AButton type="text" @click="emit('close')">
        <template #icon><CloseOutlined /></template>
      </AButton>
    </header>

    <div class="validation-body">
      <div v-if="hasResult && !errors.length && !warnings.length" class="validation-success">
        <CheckCircleOutlined />
        <div>
          <div class="success-title">校验通过</div>
          <div class="success-desc">当前流程结构、节点配置和输入引用均可用于发布或调试。</div>
        </div>
      </div>

      <template v-else-if="hasResult">
        <section v-if="errors.length" class="validation-section">
          <div class="section-title error">
            <ExclamationCircleOutlined />
            必须修复
          </div>
          <div class="validation-list">
            <button
              v-for="(item, index) in errors"
              :key="`error-${index}`"
              class="validation-item"
              :class="{ clickable: item.nodeId }"
              type="button"
              @click="item.nodeId && emit('focusNode', item.nodeId)"
            >
              <span class="item-dot error"></span>
              <span class="item-copy">
                <span class="item-title">{{ nodeLabel(item.nodeId) }}</span>
                <span class="item-desc">{{ item.field ? `${item.field}：` : '' }}{{ item.message || '配置错误' }}</span>
              </span>
            </button>
          </div>
        </section>

        <section v-if="warnings.length" class="validation-section">
          <div class="section-title warning">
            <WarningOutlined />
            建议关注
          </div>
          <div class="validation-list">
            <button
              v-for="(item, index) in warnings"
              :key="`warning-${index}`"
              class="validation-item"
              :class="{ clickable: item.nodeId }"
              type="button"
              @click="item.nodeId && emit('focusNode', item.nodeId)"
            >
              <span class="item-dot warning"></span>
              <span class="item-copy">
                <span class="item-title">{{ nodeLabel(item.nodeId) }}</span>
                <span class="item-desc">{{ item.field ? `${item.field}：` : '' }}{{ item.message || '建议检查' }}</span>
              </span>
            </button>
          </div>
        </section>
      </template>

      <AEmpty v-else description="点击顶部校验后查看结果" />
    </div>
  </aside>
</template>

<style scoped lang="scss">
.validation-panel {
  position: absolute;
  right: 16px;
  top: 60px;
  bottom: 18px;
  z-index: 17;
  min-width: 442px;
  max-width: 55vw;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

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
  background: #1677FF;
}

.validation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
}

.validation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #262626;
  font-size: 15px;
  font-weight: 700;
}

.validation-subtitle,
.success-desc,
.item-desc {
  color: #8c8c8c;
  font-size: 12px;
}

.validation-body {
  min-height: 0;
  overflow: auto;
  padding: 14px;
}

.validation-success {
  display: flex;
  gap: 10px;
  padding: 14px;
  border: 1px solid #d9f7be;
  border-radius: 8px;
  background: #f6ffed;
  color: #389e0d;
}

.success-title {
  color: #262626;
  font-weight: 700;
}

.validation-section {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
}

.section-title.error {
  color: #cf1322;
}

.section-title.warning {
  color: #d48806;
}

.validation-list {
  display: grid;
  gap: 8px;
}

.validation-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: default;
}

.validation-item.clickable {
  cursor: pointer;
}

.validation-item.clickable:hover {
  background: #fafafa;
  border-color: #d9d9d9;
}

.item-dot {
  width: 7px;
  height: 7px;
  margin-top: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.item-dot.error {
  background: #ff4d4f;
}

.item-dot.warning {
  background: #faad14;
}

.item-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.item-title {
  color: #262626;
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-desc {
  line-height: 1.5;
}

@media (max-width: 900px) {
  .validation-panel {
    left: 12px;
    right: 12px;
    top: auto;
    width: auto;
    max-width: none;
    height: 58vh;
  }

  .resize-handle {
    display: none;
  }
}
</style>

<script setup lang="ts">
/**
 * 自定义组件 props 配置弹窗：list 编辑，每条 key + type + value，
 * value 控件按类型定制（string/number/boolean/array/date/object）。
 * 打开时自动识别所选组件声明的 props（含默认值）预填，已配置条目保留用户值。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { PanelPropItem, PanelPropType } from '@/types/dashboard'
import { loadPortalPropDefs } from './portalRegistry'

const props = defineProps<{
  open: boolean
  items: PanelPropItem[]
  /** 所选 portal 组件标识，用于自动识别 props 声明 */
  componentId?: string
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'save', items: PanelPropItem[]): void
}>()

const openLocal = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const typeOptions: { label: string; value: PanelPropType }[] = [
  { label: '字符串', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔', value: 'boolean' },
  { label: '数组', value: 'array' },
  { label: '日期', value: 'date' },
  { label: '对象', value: 'object' },
]

const list = ref<PanelPropItem[]>([])
/** 自动识别出的 key 集合（用于行标注） */
const detectedKeys = ref<Set<string>>(new Set())
const detecting = ref(false)

watch(
  () => props.open,
  async (v) => {
    if (!v) return
    list.value = JSON.parse(JSON.stringify(props.items || []))
    detectedKeys.value = new Set()
    if (!props.componentId) return
    detecting.value = true
    try {
      const defs = await loadPortalPropDefs(props.componentId)
      detectedKeys.value = new Set(defs.map((d) => d.key))
      // 合并：已配置的保留用户值，未配置的按声明预填（含默认值）
      const existing = new Set(list.value.map((it) => it.key))
      defs.forEach((d) => {
        if (!existing.has(d.key)) {
          list.value.push({ key: d.key, type: d.type, value: d.defaultValue })
        }
      })
    } finally {
      detecting.value = false
    }
  },
  { immediate: true },
)

function add() {
  list.value.push({ key: '', type: 'string', value: '' })
}

function remove(idx: number) {
  list.value.splice(idx, 1)
}

/** 类型切换时重置为该类型的合理默认值，避免残留不兼容数据 */
function onTypeChange(it: PanelPropItem) {
  const defaults: Record<PanelPropType, unknown> = {
    string: '',
    number: 0,
    boolean: false,
    array: '[]',
    date: '',
    object: '{}',
  }
  it.value = defaults[it.type]
}

const KEY_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/

/** 校验全部条目，返回错误消息（null 表示通过） */
function validateList(): string | null {
  const seen = new Set<string>()
  for (const it of list.value) {
    if (!it.key) return '每个 prop 都需填写 key'
    if (!KEY_PATTERN.test(it.key)) {
      return `key ${it.key} 非法：需以字母开头，仅含字母/数字/下划线`
    }
    if (seen.has(it.key)) return `key ${it.key} 重复`
    seen.add(it.key)
    if (it.type === 'array' || it.type === 'object') {
      const raw = String(it.value ?? '').trim()
      if (!raw) return `「${it.key}」需填写 JSON 内容`
      try {
        const parsed = JSON.parse(raw)
        if (it.type === 'array' && !Array.isArray(parsed)) return `「${it.key}」必须是 JSON 数组`
        if (it.type === 'object' && (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed))) {
          return `「${it.key}」必须是 JSON 对象`
        }
      } catch {
        return `「${it.key}」不是合法的 JSON`
      }
    }
    if (it.type === 'number' && (it.value === null || it.value === undefined || it.value === '')) {
      return `「${it.key}」需填写数字`
    }
    if (it.type === 'date' && !it.value) {
      return `「${it.key}」需选择日期`
    }
  }
  return null
}

function onOk() {
  const error = validateList()
  if (error) {
    message.warning(error)
    return
  }
  emit('save', JSON.parse(JSON.stringify(list.value)))
  openLocal.value = false
}
</script>

<template>
  <a-modal v-model:open="openLocal" title="配置组件 props" width="640px" ok-text="保存" @ok="onOk">
    <p class="pc-hint">
      已自动识别组件声明的 props（含默认值，标「自动」），也可手动添加。
      按声明的类型传递：数组/对象填 JSON，日期以 <code>Date</code> 对象注入，数字/布尔按原生类型注入。
    </p>
    <div v-if="detecting" class="pc-detecting">正在识别组件 props...</div>
    <div class="props-config">
      <div class="pc-list">
        <div v-for="(it, idx) in list" :key="idx" class="pc-row">
          <div class="pc-key-wrap">
            <a-input v-model:value="it.key" placeholder="key(英文)" class="pc-key" />
            <span v-if="detectedKeys.has(it.key)" class="pc-auto-tag">自动</span>
          </div>
          <a-select
            v-model:value="it.type"
            :options="typeOptions"
            class="pc-type"
            @change="onTypeChange(it)"
          />
          <div class="pc-value">
            <a-input
              v-if="it.type === 'string'"
              v-model:value="it.value as string"
              placeholder="文本值"
            />
            <a-input-number
              v-else-if="it.type === 'number'"
              v-model:value="it.value as number"
              style="width: 100%"
              placeholder="数字值"
            />
            <a-switch v-else-if="it.type === 'boolean'" v-model:checked="it.value as boolean" />
            <a-date-picker
              v-else-if="it.type === 'date'"
              v-model:value="it.value as string"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
            <a-textarea
              v-else-if="it.type === 'array'"
              v-model:value="it.value as string"
              :rows="2"
              placeholder='如 [1, 2, "a"]'
            />
            <a-textarea
              v-else
              v-model:value="it.value as string"
              :rows="2"
              placeholder='如 { "a": 1 }'
            />
          </div>
          <a-button type="text" danger @click="remove(idx)">
            <template #icon><DeleteOutlined /></template>
          </a-button>
        </div>
      </div>
      <a-button type="dashed" block @click="add">
        <template #icon><PlusOutlined /></template>
        添加 prop
      </a-button>
    </div>
  </a-modal>
</template>

<style scoped lang="scss">
.pc-hint {
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

.props-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pc-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pc-list:empty {
  display: none;
}

.pc-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.pc-key-wrap {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  width: 150px;
}

.pc-key {
  width: 100%;
}

.pc-auto-tag {
  position: absolute;
  right: 6px;
  padding: 0 4px;
  border-radius: 4px;
  background: #f0f5ff;
  font-size: 10px;
  color: #1677ff;
  pointer-events: none;
}

.pc-detecting {
  margin-bottom: 10px;
  font-size: 12px;
  color: #bbb;
}

/* 类型选择器固定宽度，保证选项文本完整可见 */
.pc-type {
  flex-shrink: 0;
  width: 96px;
}

.pc-value {
  flex: 1;
  display: flex;
  align-items: center;
  min-height: 32px;
}
</style>

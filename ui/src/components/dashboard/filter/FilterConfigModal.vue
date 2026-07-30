<script setup lang="ts">
/**
 * 筛选器配置弹窗：在设计器中增删改全局筛选器。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { DashboardFilter } from '@/types/dashboard'

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
  { label: '下拉选择', value: 'select' },
  { label: '文本', value: 'text' },
]

const items = ref<DashboardFilter[]>([])
// 下拉选项以原始多行文本本地编辑，保存时再解析，避免输入换行被即时解析清除
const optionsTextMap = ref<Record<string, string>>({})

watch(
  () => props.open,
  (v) => {
    if (v) {
      items.value = JSON.parse(JSON.stringify(props.filters || []))
      const map: Record<string, string> = {}
      items.value.forEach((it) => {
        map[it.id] = optionsToText(it.options)
      })
      optionsTextMap.value = map
    }
  },
  { immediate: true },
)

function add() {
  const id = 'f-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5)
  items.value.push({ id, type: 'text', label: '', paramKey: '', options: [] })
  optionsTextMap.value[id] = ''
}

function remove(idx: number) {
  items.value.splice(idx, 1)
}

function optionsToText(options?: { label: string; value: string }[]): string {
  return (options || [])
    .map((o) => (o.label === o.value ? o.value : `${o.label}=${o.value}`))
    .join('\n')
}

function parseOptions(text: string): { label: string; value: string }[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => {
      const idx = l.indexOf('=')
      if (idx > -1) {
        return { label: l.slice(0, idx).trim(), value: l.slice(idx + 1).trim() }
      }
      return { label: l, value: l }
    })
}

function onOk() {
  for (const it of items.value) {
    if (!it.label || !it.paramKey) {
      message.warning('每个筛选器都需填写显示名称与参数名')
      return
    }
    it.options = parseOptions(optionsTextMap.value[it.id] || '')
  }
  emit('save', JSON.parse(JSON.stringify(items.value)))
  openLocal.value = false
}
</script>

<template>
  <a-modal v-model:open="openLocal" title="配置筛选器" width="640px" ok-text="保存" @ok="onOk">
    <p class="fc-hint">
      筛选器改变时联动刷新所有面板。参数名会作为数据集命名参数注入：文本/下拉为
      <code>:参数名</code>，日期范围为 <code>:参数名Start</code> 与 <code>:参数名End</code>。
    </p>
    <div class="filter-config">
      <div v-for="(it, idx) in items" :key="it.id" class="fc-row">
        <div class="fc-grid">
          <a-input v-model:value="it.label" placeholder="显示名称" />
          <a-select v-model:value="it.type" :options="typeOptions" style="width: 120px" />
          <a-input v-model:value="it.paramKey" placeholder="参数名(英文)" />
          <a-button type="text" danger @click="remove(idx)">
            <template #icon><DeleteOutlined /></template>
          </a-button>
        </div>
        <a-textarea
          v-if="it.type === 'select'"
          v-model:value="optionsTextMap[it.id]"
          :rows="3"
          placeholder="每行一个选项，格式：显示名=值（不带=则显示名与值相同）"
        />
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
</style>

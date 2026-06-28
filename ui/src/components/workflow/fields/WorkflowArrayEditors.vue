<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { WorkflowFieldOption } from '@/types/workflow'

type EditorType = 'keyValue' | 'dbParams' | 'startParams' | 'stringList' | 'matchList'

const props = defineProps<{
  modelValue?: unknown
  type: EditorType
  options?: WorkflowFieldOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown[]]
}>()

const rows = computed<unknown[]>(() => (Array.isArray(props.modelValue) ? props.modelValue : []))

function cloneRows() {
  return rows.value.map((item) => ({ ...(typeof item === 'object' && item ? item : { value: item }) }))
}

function addRow() {
  const defaults: Record<EditorType, unknown> = {
    keyValue: { key: '', value: '' },
    dbParams: { value: '', type: 'STRING' },
    startParams: { position: 'QUERY', name: '', value: '', type: 'String', required: false, remark: '' },
    stringList: '',
    matchList: { matchValue: '', nextNodeId: '' },
  }
  emit('update:modelValue', [...rows.value, defaults[props.type]])
}

function removeRow(index: number) {
  emit('update:modelValue', rows.value.filter((_, rowIndex) => rowIndex !== index))
}

function updateObject(index: number, key: string, value: unknown) {
  const next = cloneRows()
  next[index] = { ...(next[index] || {}), [key]: value }
  emit('update:modelValue', next)
}

function updateString(index: number, value: string) {
  const next = [...rows.value]
  next[index] = value
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="array-editor">
    <div v-if="rows.length" class="array-list">
      <div v-for="(row, index) in rows" :key="index" class="array-row" :class="`row-${type}`">
        <template v-if="type === 'stringList'">
          <AInput :value="String(row ?? '')" placeholder="请输入值" @update:value="(value: string) => updateString(index, value)" />
        </template>

        <template v-else-if="type === 'dbParams'">
          <AInput :value="(row as any).value" placeholder="参数值，支持 ${变量}" @update:value="(value: string) => updateObject(index, 'value', value)" />
          <ASelect
            :value="(row as any).type || 'STRING'"
            :options="options"
            @update:value="(value: string) => updateObject(index, 'type', value)"
          />
        </template>

        <template v-else-if="type === 'startParams'">
          <AInput :value="(row as any).name" placeholder="参数名" @update:value="(value: string) => updateObject(index, 'name', value)" />
          <AInput :value="(row as any).value" placeholder="默认值" @update:value="(value: string) => updateObject(index, 'value', value)" />
          <ASelect :value="(row as any).type || 'String'" :options="options" @update:value="(value: string) => updateObject(index, 'type', value)" />
          <ACheckbox :checked="Boolean((row as any).required)" @update:checked="(value: boolean) => updateObject(index, 'required', value)">必填</ACheckbox>
          <AInput :value="(row as any).remark" placeholder="备注" @update:value="(value: string) => updateObject(index, 'remark', value)" />
        </template>

        <template v-else-if="type === 'matchList'">
          <AInput :value="(row as any).matchValue" placeholder="匹配值" @update:value="(value: string) => updateObject(index, 'matchValue', value)" />
          <AInput :value="(row as any).nextNodeId" placeholder="后续节点ID" @update:value="(value: string) => updateObject(index, 'nextNodeId', value)" />
        </template>

        <template v-else>
          <AInput :value="(row as any).key" placeholder="Key" @update:value="(value: string) => updateObject(index, 'key', value)" />
          <AInput :value="(row as any).value" placeholder="Value" @update:value="(value: string) => updateObject(index, 'value', value)" />
        </template>

        <AButton type="text" danger @click="removeRow(index)">
          <template #icon><DeleteOutlined /></template>
        </AButton>
      </div>
    </div>

    <div v-else class="array-empty">暂无数据</div>

    <AButton block size="small" class="add-row" @click="addRow">
      <template #icon><PlusOutlined /></template>
      添加一项
    </AButton>
  </div>
</template>

<style scoped lang="scss">
.array-editor {
  display: grid;
  gap: 8px;
}

.array-list {
  display: grid;
  gap: 8px;
}

.array-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(110px, 140px) 32px;
  gap: 8px;
  align-items: center;
}

.row-stringList {
  grid-template-columns: minmax(0, 1fr) 32px;
}

.row-startParams {
  grid-template-columns: minmax(90px, 1fr) minmax(90px, 1fr) minmax(80px, 1fr) 60px minmax(50px, 1fr) 22px;
}

.array-empty {
  padding: 10px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  color: #8c8c8c;
  font-size: 12px;
  text-align: center;
}

.add-row {
  border-style: dashed;
}

@media (max-width: 720px) {
  .array-row,
  .row-startParams {
    grid-template-columns: 1fr;
  }
}
</style>

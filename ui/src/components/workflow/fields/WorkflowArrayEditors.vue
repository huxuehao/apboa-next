<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined, FileTextOutlined, PlusOutlined } from '@ant-design/icons-vue'
import BlurInput from '@/components/workflow/panels/shared/BlurInput.vue'
import BlurTextarea from '@/components/workflow/panels/shared/BlurTextarea.vue'

type EditorType = 'keyValue' | 'dbParams' | 'startParams' | 'stringList' | 'matchList'

const props = defineProps<{
  modelValue?: unknown
  type: EditorType
  options?: any[]
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
    startParams: { position: 'QUERY', name: '', value: '', type: 'String', required: false, description: '' },
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
          <BlurInput :model-value="String(row ?? '')" placeholder="请输入值" @update:model-value="(value: string) => updateString(index, value)" />
        </template>

        <template v-else-if="type === 'dbParams'">
          <BlurInput :model-value="(row as any).value" placeholder="参数值，支持 ${变量}" @update:model-value="(value: string) => updateObject(index, 'value', value)" />
          <ASelect
            :value="(row as any).type || 'STRING'"
            :options="options"
            @update:value="(value: string) => updateObject(index, 'type', value)"
          />
        </template>

        <template v-else-if="type === 'startParams'">
          <div class="field-cell">
            <span v-if="index === 0" class="field-title">参数名</span>
            <BlurInput :model-value="(row as any).name" placeholder="参数名" @update:model-value="(value: string) => updateObject(index, 'name', value)" />
          </div>
          <div class="field-cell">
            <span v-if="index === 0" class="field-title">默认值</span>
            <BlurInput :model-value="(row as any).value" placeholder="默认值" @update:model-value="(value: string) => updateObject(index, 'value', value)" />
          </div>
          <div class="field-cell">
            <span v-if="index === 0" class="field-title">类型</span>
            <ASelect :value="(row as any).type || 'String'" :options="options" @update:value="(value: string) => updateObject(index, 'type', value)" />
          </div>
          <div class="field-cell">
            <span v-if="index === 0" class="field-title-spacer" />
            <ACheckbox :checked="Boolean((row as any).required)" @update:checked="(value: boolean) => updateObject(index, 'required', value)">必填</ACheckbox>
          </div>
          <div class="field-cell">
            <span v-if="index === 0" class="field-title-spacer" />
            <APopover trigger="click" placement="bottomLeft" :overlay-style="{ minWidth: '260px' }">
              <template #content>
                <div class="desc-popover">
                  <BlurTextarea
                    :model-value="(row as any).description"
                    placeholder="请输入描述"
                    :rows="3"
                    @update:model-value="(value: string) => updateObject(index, 'description', value)"
                  />
                </div>
              </template>
              <ATooltip
                :title="(row as any).description || undefined"
                :overlay-inner-style="{ maxWidth: '150px' }"
              >
                <span class="desc-icon" :class="{ 'has-content': (row as any).description }" title="描述">
                  <FileTextOutlined />
                </span>
              </ATooltip>
            </APopover>
          </div>
        </template>

        <template v-else-if="type === 'matchList'">
          <BlurInput :model-value="(row as any).matchValue" placeholder="匹配值" @update:model-value="(value: string) => updateObject(index, 'matchValue', value)" />
          <BlurInput :model-value="(row as any).nextNodeId" placeholder="后续节点ID" @update:model-value="(value: string) => updateObject(index, 'nextNodeId', value)" />
        </template>

        <template v-else>
          <BlurInput :model-value="(row as any).key" placeholder="Key" @update:model-value="(value: string) => updateObject(index, 'key', value)" />
          <BlurInput :model-value="(row as any).value" placeholder="Value" @update:model-value="(value: string) => updateObject(index, 'value', value)" />
        </template>

        <template v-if="type === 'startParams'">
          <div class="field-cell">
            <span v-if="index === 0" class="field-title-spacer" />
            <AButton type="text" danger @click="removeRow(index)" >
              <template #icon><DeleteOutlined /></template>
            </AButton>
          </div>
        </template>
        <AButton v-else type="text" danger @click="removeRow(index)" style="background-color: #FFF2F0;">
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
  grid-template-columns: minmax(85px, 2fr) minmax(85px, 2fr) minmax(100px, 1fr) 60px 22px 22px;
}

.array-empty {
  padding: 10px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  color: #8c8c8c;
  font-size: 12px;
  text-align: center;
}

.field-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.field-title-spacer {
  height: 18px;
  visibility: hidden;
}

.field-title {
  font-size: 12px;
  color: #bfbfbf;
  text-align: left;
  line-height: 1.5;
  user-select: none;
  white-space: nowrap;
}

.desc-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  cursor: pointer;
  color: #d9d9d9;
  transition: color 0.2s, background-color 0.2s;
  font-size: 13px;

  &:hover {
    color: #1677ff;
    background-color: rgba(22, 119, 255, 0.06);
  }

  &.has-content {
    color: #1677ff;
  }
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

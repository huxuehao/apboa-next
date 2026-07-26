<script setup lang="ts">
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import NextNodeSelector from '@/components/workflow/bindings/NextNodeSelector.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode } from '@/types/workflow'

interface MatchItem {
  matchValue: string
  nextNodeId: string
}

const props = defineProps<{
  modelValue?: MatchItem[]
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  currentNodeId: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MatchItem[]]
}>()

// 更新指定行的字段并向外发射
function updateItem(index: number, patch: Partial<MatchItem>) {
  const matches = (props.modelValue || []).map((m, i) => (i === index ? { ...m, ...patch } : m))
  emit('update:modelValue', matches)
}

function addItem() {
  emit('update:modelValue', [...(props.modelValue || []), { matchValue: '', nextNodeId: '' }])
}

function removeItem(index: number) {
  emit('update:modelValue', (props.modelValue || []).filter((_, i) => i !== index))
}
</script>

<template>
  <div class="match-binding-editor" :class="{ empty: !(modelValue || []).length }">
    <div v-if="$slots.prefix" class="editor-prefix">
      <slot name="prefix" />
    </div>
    <div v-if="(modelValue || []).length" class="match-binding-list">
      <div v-for="(item, index) in modelValue || []" :key="index" class="match-binding-row">
        <div class="match-value-cell">
          <AInput
            :value="item.matchValue"
            placeholder="匹配值"
            @update:value="(v: string) => updateItem(index, { matchValue: v })"
          />
        </div>
        <div>→</div>
        <div class="match-next-cell">
          <NextNodeSelector
            :nodes="nodes"
            :edges="edges"
            :current-node-id="currentNodeId"
            :selected-node-id="item.nextNodeId || undefined"
            @select="(id: string) => updateItem(index, { nextNodeId: id })"
            @clear="updateItem(index, { nextNodeId: '' })"
          />
        </div>
        <DeleteOutlined class="row-remove" @click="removeItem(index)" />
      </div>
    </div>
    <AButton type="dashed" block class="add-btn" @click="addItem">
      <PlusOutlined /> 添加匹配项
    </AButton>
  </div>
</template>

<style scoped lang="scss">
// 两列网格：左列为外部传入的前缀控件，右列第一行为匹配行列表，第二行为添加按钮。
// 前缀控件仅相对匹配行列表垂直居中，不受添加按钮高度影响。
.match-binding-editor {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  column-gap: 5px;
  row-gap: 8px;
  min-width: 0;
}

.editor-prefix {
  grid-column: 1;
  grid-row: 1;
  align-self: center;
}

.match-binding-list {
  grid-column: 2;
  grid-row: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  min-width: 0;
}

.add-btn {
  grid-column: 2;
  grid-row: 2;
  color: #595959;
}

// 无匹配项时，添加按钮提升至第一行，与前缀控件保持水平对齐
.match-binding-editor.empty .add-btn {
  grid-row: 1;
  align-self: center;
}

.match-binding-row {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.match-value-cell {
  flex: 1;
  min-width: 0;
}

.match-next-cell {
  flex: 2;
  min-width: 0;
}

.row-remove {
  flex-shrink: 0;
  color: #bfbfbf;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #ff4d4f;
  }
}
</style>

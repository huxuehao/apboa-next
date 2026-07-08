<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CloseCircleFilled } from '@ant-design/icons-vue'
import IconFont from '@/components/common/IconFont.vue'
import { getNodeIconName } from '@/config/workflow/common'
import { getWorkflowNodeSchema } from '@/config/workflow/nodeSchemas'

interface EntryCandidate {
  id: string
  name: string
  type: string
  color: string
}

const props = withDefaults(
  defineProps<{
    modelValue: string | null
    subNodes: Array<Record<string, unknown>>
    subEdges: Array<Record<string, unknown>>
  }>(),
  {
    subNodes: () => [],
    subEdges: () => [],
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

const popoverOpen = ref(false)

const candidates = computed<EntryCandidate[]>(() => {
  const targetIds = new Set(props.subEdges.map((e) => String(e.target || '')).filter(Boolean))
  return props.subNodes
    .filter((n) => !targetIds.has(String(n.id || '')))
    .map((n) => ({
      id: String(n.id || ''),
      name: String(n.name || n.id || ''),
      type: String(n.type || ''),
      color: getWorkflowNodeSchema(String(n.type || ''))?.color || '#1677ff',
    }))
})

watch(
  candidates,
  (list) => {
    if (!props.modelValue && list.length > 0 && list[0]) {
      emit('update:modelValue', list[0].id)
    }
  },
  { immediate: true },
)

const selectedLabel = computed(() => {
  if (!props.modelValue) return ''
  const found = candidates.value.find((c) => c.id === props.modelValue)
  return found?.name || ''
})

const selectedType = computed(() => {
  if (!props.modelValue) return ''
  const found = candidates.value.find((c) => c.id === props.modelValue)
  return found?.type || ''
})

const selectedColor = computed(() => {
  if (!props.modelValue) return '#1677ff'
  const found = candidates.value.find((c) => c.id === props.modelValue)
  return found?.color || '#1677ff'
})

function select(id: string) {
  emit('update:modelValue', id)
  popoverOpen.value = false
}

function clear() {
  emit('update:modelValue', null)
}
</script>

<template>
  <APopover
    v-model:open="popoverOpen"
    trigger="click"
    placement="bottomLeft"
    :overlay-inner-style="{ padding: 0 }"
  >
    <div class="entry-trigger" :class="{ placeholder: !selectedLabel }">
      <span v-if="!selectedLabel" class="trigger-placeholder">选择入口节点...</span>
      <span v-else class="trigger-content">
        <span class="trigger-name">
          <IconFont
            :name="getNodeIconName(selectedType)"
            :size="14"
            :color="selectedColor"
            style="flex-shrink: 0"
          />
          {{ selectedLabel }}
        </span>
      </span>
      <CloseCircleFilled v-if="selectedLabel" class="trigger-clear" @click.stop="clear" />
    </div>
    <template #content>
      <div class="entry-dropdown">
        <div v-if="!candidates.length" class="dropdown-empty">暂无可选入口节点</div>
        <div
          v-for="c in candidates"
          :key="c.id"
          class="entry-row"
          :class="{ selected: modelValue === c.id }"
          @click="select(c.id)"
        >
          <IconFont
            :name="getNodeIconName(c.type)"
            :size="14"
            :color="c.color"
            style="flex-shrink: 0; margin-top: 1px"
          />
          <div class="entry-text">
            <span class="entry-name">{{ c.name }}</span>
            <span class="entry-id">{{ c.id }}</span>
          </div>
        </div>
      </div>
    </template>
  </APopover>
</template>

<style scoped lang="scss">
.entry-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 2px 8px;
  background-color: #f2f4f7;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  min-height: 32px;
  &.placeholder {
    color: #bfbfbf;
  }
}
.trigger-placeholder {
  flex: 1;
  color: #bfbfbf;
}
.trigger-content {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #fff;
  overflow: hidden;
}
.trigger-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
}
.trigger-clear {
  flex-shrink: 0;
  color: #bfbfbf;
  font-size: 12px;
  cursor: pointer;
  &:hover {
    color: #595959;
  }
}

.entry-dropdown {
  width: 300px;
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.entry-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
  &:hover {
    background: #f5f5f5;
  }
  &.selected {
    background: #f5f5f5;
  }
}
.entry-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.entry-name {
  font-size: 13px;
  color: #262626;
  font-weight: 600;
}
.entry-id {
  font-size: 11px;
  color: #a8a8a8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dropdown-empty {
  padding: 24px 16px;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
}
</style>

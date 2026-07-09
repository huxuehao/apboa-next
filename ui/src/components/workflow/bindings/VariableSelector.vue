<script setup lang="ts">
import { computed, ref, watch, inject } from 'vue'
import type { Ref } from 'vue'
import { SearchOutlined, CloseCircleFilled } from '@ant-design/icons-vue'
import type { WorkflowVariable } from '@/types/workflow'

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  clear: []
}>()

// ========== 注入数据 ==========

const workflowVariables = inject<Ref<WorkflowVariable[]>>('workflowVariables', ref([]))

// ========== 系统变量 ==========

const systemVariables: WorkflowVariable[] = [
  { id: 'sys_1', name: 'tenantId', type: 'number', source: 'system', description: '当前租户ID' },
  { id: 'sys_2', name: 'tenantCode', type: 'string', source: 'system', description: '当前租户编号' },
  { id: 'sys_3', name: 'userId', type: 'number', source: 'system', description: '当前用户ID' },
  { id: 'sys_4', name: 'userName', type: 'string', source: 'system', description: '当前用户名称' },
]

// ========== 状态 ==========

const popoverOpen = ref(false)
const searchText = ref('')

// ========== 计算属性 ==========

const allVariables = computed(() => [...systemVariables, ...workflowVariables.value])

const selectedVariable = computed(() =>
  props.modelValue ? allVariables.value.find((v) => v.name === props.modelValue) : null,
)

const filteredVariables = computed(() => {
  const query = searchText.value.trim().toLowerCase()
  if (!query) return allVariables.value
  return allVariables.value.filter(
    (v) => v.name.toLowerCase().includes(query) || v.type.toLowerCase().includes(query),
  )
})

const filteredSystemVariables = computed(() =>
  filteredVariables.value.filter((v) => v.source === 'system'),
)

const filteredCustomVariables = computed(() =>
  filteredVariables.value.filter((v) => v.source === 'custom'),
)

// ========== 方法 ==========

function selectVariable(name: string) {
  emit('update:modelValue', name)
  popoverOpen.value = false
  searchText.value = ''
}

function clearSelection() {
  emit('clear')
  emit('update:modelValue', '')
}

watch(popoverOpen, (open) => {
  if (!open) searchText.value = ''
})
</script>

<template>
  <APopover
    v-model:open="popoverOpen"
    trigger="click"
    placement="bottomLeft"
    :overlay-inner-style="{ padding: 0 }"
  >
    <div class="selector-trigger" :class="{ placeholder: !selectedVariable }">
      <span v-if="selectedVariable" class="trigger-content">
        <span class="trigger-name">{{ selectedVariable.name }}</span>
        <span class="trigger-type">{{ selectedVariable.type }}</span>
      </span>
      <span v-else class="trigger-placeholder">选择变量...</span>
      <CloseCircleFilled
        v-if="selectedVariable"
        class="trigger-clear"
        @click.stop="clearSelection"
      />
    </div>

    <template #content>
      <div class="selector-dropdown">
        <div class="dropdown-search">
          <span class="search-icon"><SearchOutlined /></span>
          <input
            v-model="searchText"
            type="text"
            class="search-input"
            placeholder="搜索变量名或类型..."
            @click.stop
          />
        </div>

        <div class="dropdown-list" :class="{ empty: filteredVariables.length === 0 }">
          <!-- 系统变量 -->
          <div v-for="item in filteredSystemVariables" :key="item.id" class="variable-row" :class="{ selected: modelValue === item.name }" @click="selectVariable(item.name)" :title="item.description">
            <span class="variable-name">{{ item.name }}</span>
            <span class="variable-type">{{ item.type }}</span>
          </div>

          <!-- 自定义变量分隔线 -->
          <div v-if="filteredCustomVariables.length > 0" class="source-divider">
            <span class="source-divider-text">自定义变量</span>
          </div>

          <!-- 自定义变量 -->
          <div v-for="item in filteredCustomVariables" :key="item.id" class="variable-row" :class="{ selected: modelValue === item.name }" @click="selectVariable(item.name)">
            <span class="variable-name">{{ item.name }}</span>
            <span class="variable-type">{{ item.type }}</span>
          </div>

          <!-- 空状态 -->
          <div v-if="filteredVariables.length === 0" class="dropdown-empty">
            无匹配的变量
          </div>
        </div>
      </div>
    </template>
  </APopover>
</template>

<style scoped lang="scss">
.selector-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 2px 8px;
  background-color: #F2F4F7;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: border-color 0.2s;
  min-height: 32px;
  position: relative;

  &.placeholder {
    color: #bfbfbf;
  }
}

.trigger-clear {
  flex-shrink: 0;
  color: #bfbfbf;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #595959;
  }
}

.trigger-content {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #ffffff;
}

.trigger-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
  font-weight: 600;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
}

.trigger-type {
  flex-shrink: 0;
  font-size: 12px;
  color: #8c8c8c;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
}

.trigger-placeholder {
  flex: 1;
  color: #bfbfbf;
}

.selector-dropdown {
  width: 320px;
  padding: 8px;
}

.dropdown-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
  border-radius: 8px 8px 0 0;
}

.search-icon {
  color: #bfbfbf;
  font-size: 14px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  color: #262626;
  background: transparent;

  &::placeholder {
    color: #bfbfbf;
  }
}

.dropdown-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 320px;
  overflow-y: auto;

  &.empty {
    max-height: auto;
  }
}

.variable-row {
  display: flex;
  align-items: center;
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

.variable-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.variable-type {
  flex-shrink: 0;
  font-size: 12px;
  color: #8c8c8c;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
}

.source-divider {
  display: flex;
  align-items: center;
  padding: 10px 12px 6px;
  margin: 4px 0 0;
  border-top: 1px solid #f0f0f0;
}

.source-divider-text {
  font-size: 11px;
  color: #8c8c8c;
  letter-spacing: 0.3px;
}

.dropdown-empty {
  padding: 24px 16px;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
}
</style>

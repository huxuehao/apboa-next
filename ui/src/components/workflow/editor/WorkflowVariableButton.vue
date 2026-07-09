<script setup lang="ts">
import { ref, computed, inject, watch, nextTick, onMounted, onUnmounted } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { PlusOutlined, SearchOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import IconFont from '@/components/common/IconFont.vue'
import type { VariableType, WorkflowVariable } from '@/types/workflow'

// ========== 系统变量 ==========

const systemVariables: WorkflowVariable[] = [
  { id: 'sys_1', name: 'tenantId', type: 'number', source: 'system', description: '当前租户ID' },
  { id: 'sys_2', name: 'tenantCode', type: 'string', source: 'system', description: '当前租户编号' },
  { id: 'sys_3', name: 'userId', type: 'number', source: 'system', description: '当前用户ID' },
  { id: 'sys_4', name: 'userName', type: 'string', source: 'system', description: '当前用户名称' },
]

// ========== 注入数据 ==========

const customVariables = inject<Ref<WorkflowVariable[]>>('workflowVariables', ref([]))
const readonly = inject<ComputedRef<boolean>>('subWorkflowActive', computed(() => false))

// ========== 状态 ==========

const modalOpen = ref(false)
const searchText = ref('')
const searchDebounceTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const debouncedSearchText = ref('')
const adding = ref(false)
const editingId = ref<string | null>(null)
const editName = ref('')
const editType = ref<VariableType>('string')
const listRef = ref<HTMLDivElement | null>(null)

// ========== 类型选项 ==========

const typeOptions: { value: VariableType; label: string }[] = [
  { value: 'string', label: 'string' },
  { value: 'number', label: 'number' },
  { value: 'boolean', label: 'boolean' },
  { value: 'object', label: 'object' },
  { value: 'array', label: 'array' },
]

// ========== 搜索防抖 ==========

watch(searchText, (val) => {
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value)
  }
  searchDebounceTimer.value = setTimeout(() => {
    debouncedSearchText.value = val
  }, 300)
})

// ========== 过滤后的变量列表 ==========

const filteredSystemVariables = computed(() => {
  const query = debouncedSearchText.value.trim().toLowerCase()
  if (!query) return systemVariables
  return systemVariables.filter(
    (v) => v.name.toLowerCase().includes(query) || v.type.toLowerCase().includes(query),
  )
})

const filteredCustomVariables = computed(() => {
  const query = debouncedSearchText.value.trim().toLowerCase()
  if (!query) return customVariables.value
  return customVariables.value.filter(
    (v) => v.name.toLowerCase().includes(query) || v.type.toLowerCase().includes(query),
  )
})

// ========== 面板控制 ==========

function openModal() {
  modalOpen.value = true
}

function closeModal() {
  modalOpen.value = false
  adding.value = false
  editingId.value = null
  searchText.value = ''
  debouncedSearchText.value = ''
}

// ========== 自定义变量 CRUD ==========

function startAdd() {
  adding.value = true
  editingId.value = null
  editName.value = ''
  editType.value = 'string'
  nextTick(() => {
    scrollToBottom()
  })
}

function startEdit(item: WorkflowVariable) {
  if (readonly.value) return
  adding.value = false
  editingId.value = item.id
  editName.value = item.name
  editType.value = item.type
}

function saveCurrentEdit() {
  const name = editName.value.trim()
  const type = editType.value

  if (!name && !type) {
    // 清空则删除
    if (editingId.value) {
      customVariables.value = customVariables.value.filter((v) => v.id !== editingId.value)
    }
    cancelEdit()
    return
  }

  if (!name) {
    message.warning('变量名不能为空')
    return
  }

  if (editingId.value) {
    // 编辑模式
    const item = customVariables.value.find((v) => v.id === editingId.value)
    if (item) {
      item.name = name
      item.type = type
    }
  } else {
    // 新增模式
    customVariables.value.push({
      id: `cust_${Date.now()}`,
      name,
      type,
      source: 'custom',
    })
  }
  cancelEdit()
}

function cancelEdit() {
  adding.value = false
  editingId.value = null
  editName.value = ''
  editType.value = 'string'
}

function deleteVariable(id: string) {
  customVariables.value = customVariables.value.filter((v) => v.id !== id)
  cancelEdit()
}

function scrollToBottom() {
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

// ========== 键盘关闭 ==========

function handleEscape(e: KeyboardEvent) {
  if (e.key === 'Escape' && modalOpen.value) {
    if (adding.value || editingId.value) {
      cancelEdit()
    }
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscape)
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value)
  }
})
</script>

<template>
  <!-- 触发按钮 -->
  <ATooltip placement="right" title="工作流变量">
    <button
      type="button"
      class="variable-trigger-btn"
      :class="{ active: modalOpen }"
      @click="openModal"
    >
      <IconFont name="nodevariable" :size="22" color="#975ADB" />
    </button>
  </ATooltip>

  <!-- 变量管理弹窗 -->
  <AModal
    v-model:open="modalOpen"
    title="工作流变量"
    width="520px"
    :footer="null"
    :destroy-on-close="false"
    :mask-closable="false"
    @cancel="closeModal"
  >
    <div class="var-modal">
      <!-- 顶部操作栏：添加按钮 + 搜索框 -->
      <div class="var-toolbar">
        <AButton
          v-if="!readonly"
          size="small"
          type="primary"
          :disabled="adding || editingId !== null"
          @click="startAdd"
        >
          <template #icon><PlusOutlined /></template>
          添加变量
        </AButton>
        <div class="var-search">
          <SearchOutlined class="search-icon" />
          <input
            v-model="searchText"
            type="text"
            class="search-input"
            placeholder="搜索变量名或类型..."
          />
        </div>
      </div>

      <!-- 变量列表 -->
      <div ref="listRef" class="var-list-container">
        <!-- 系统变量 -->
        <ATooltip
          v-for="item in filteredSystemVariables"
          :key="item.id"
          :title="item.description"
          placement="left"
        >
          <div class="var-row var-row-system">
            <div class="var-row-content">
              <span class="var-name">{{ item.name }}</span>
              <span class="var-type">{{ item.type }}</span>
            </div>
          </div>
        </ATooltip>

        <!-- 自定义变量分隔线 -->
        <div v-if="filteredCustomVariables.length > 0" class="var-divider">
          <span class="var-divider-text">自定义变量</span>
        </div>

        <!-- 自定义变量 -->
        <div
          v-for="item in filteredCustomVariables"
          :key="item.id"
          class="var-row var-row-custom"
          :class="{ editing: editingId === item.id }"
          @click="startEdit(item)"
        >
          <template v-if="editingId === item.id">
            <div class="var-edit-row">
              <input
                v-model="editName"
                class="var-edit-input"
                placeholder="变量名"
                @blur="saveCurrentEdit"
                @keydown.enter="saveCurrentEdit"
                @keydown.escape="cancelEdit"
              />
              <ASelect
                v-model:value="editType"
                size="small"
                class="var-edit-select"
                :options="typeOptions"
                @blur="saveCurrentEdit"
              />
              <button
                type="button"
                class="var-delete-btn"
                title="删除变量"
                @mousedown.prevent="deleteVariable(item.id)"
              >
                <DeleteOutlined />
              </button>
            </div>
          </template>
          <template v-else>
            <div class="var-row-content">
              <span class="var-name">{{ item.name }}</span>
              <span class="var-type">{{ item.type }}</span>
            </div>
          </template>
        </div>

        <!-- 新增行 -->
        <div v-if="adding" class="var-row var-row-custom editing">
          <div class="var-edit-row">
            <input
              v-model="editName"
              class="var-edit-input"
              placeholder="变量名"
              autofocus
              @blur="saveCurrentEdit"
              @keydown.enter="saveCurrentEdit"
              @keydown.escape="cancelEdit"
            />
            <ASelect
              v-model:value="editType"
              size="small"
              class="var-edit-select"
              :options="typeOptions"
              @blur="saveCurrentEdit"
            />
          </div>
        </div>

        <!-- 空状态 -->
        <div
          v-if="filteredSystemVariables.length === 0 && filteredCustomVariables.length === 0 && !adding"
          class="var-empty"
        >
          暂无匹配的变量
        </div>
      </div>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
// ========== 触发按钮 ==========
.variable-trigger-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #595959;
  cursor: pointer;
  transition: all 0.2s ease;
}

// ========== 弹窗容器 ==========
.var-modal {
  display: flex;
  flex-direction: column;
  min-height: 300px;
  max-height: 56vh;
}

// ========== 顶部操作栏 ==========
.var-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.var-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: #F2F4F7;
  border-radius: 6px;
}

.search-icon {
  color: #bfbfbf;
  font-size: 13px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  color: #262626;
  background: transparent;
  min-width: 0;

  &::placeholder {
    color: #bfbfbf;
  }
}

// ========== 变量列表容器 ==========
.var-list-container {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

// ========== 变量行 ==========
.var-row {
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.15s;

  & + .var-row {
    margin-top: 2px;
  }
}

.var-row-system {
  background: rgba(0, 0, 0, 0.02);
}

.var-row-custom {
  cursor: pointer;

  &:hover {
    background: rgba(0, 0, 0, 0.04);
  }

  &.editing {
    background: #e6f4ff;
    cursor: default;
  }
}

.var-row-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.var-name {
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

.var-type {
  flex-shrink: 0;
  font-size: 12px;
  color: #8c8c8c;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
}

// ========== 分隔线 ==========
.var-divider {
  display: flex;
  align-items: center;
  padding: 12px 12px 6px;
}

.var-divider-text {
  font-size: 11px;
  color: #8c8c8c;
  letter-spacing: 0.5px;
}

// ========== 编辑行 ==========
.var-edit-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.var-edit-input {
  flex: 1;
  min-width: 0;
  padding: 4px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  outline: none;
  transition: border-color 0.2s;

  &:focus {
    border-color: #1677ff;
  }
}

.var-edit-select {
  width: 100px;
}

.var-delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #bfbfbf;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;

  &:hover {
    color: #ff4d4f;
    background: #fff1f0;
  }
}

// ========== 空状态 ==========
.var-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  color: #bfbfbf;
  font-size: 13px;
}
</style>

<style>
/* 全局样式：弹窗内 ant 组件微调 */
.var-modal .ant-select-sm {
  font-size: 12px;
}
</style>

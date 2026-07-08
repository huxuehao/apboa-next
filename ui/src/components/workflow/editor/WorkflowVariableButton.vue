<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { PlusOutlined, DeleteOutlined, EditOutlined, CheckOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {getNodeIconName} from "@/config/workflow/common.ts";
import IconFont from "@/components/common/IconFont.vue";

// ========== 类型定义 ==========

type VariableType = 'string' | 'number' | 'boolean' | 'object' | 'array'

interface VariableItem {
  id: string
  name: string
  description: string
  type: VariableType
  defaultValue?: string
  source: 'system' | 'custom'
}

// ========== 状态 ==========

const modalOpen = ref(false)
const activeTab = ref<'system' | 'custom'>('system')
const adding = ref(false)
const editingId = ref<string | null>(null)
const editForm = ref({ name: '', description: '', type: 'string' as VariableType, defaultValue: '' })

// ========== 系统变量（Mock 数据） ==========

const systemVariables = ref<VariableItem[]>([
  { id: 'sys_1', name: 'workflow.id', description: '当前工作流ID', type: 'string', source: 'system' },
  { id: 'sys_2', name: 'workflow.name', description: '当前工作流名称', type: 'string', source: 'system' },
  { id: 'sys_3', name: 'tenant.id', description: '当前租户ID', type: 'string', source: 'system' },
  { id: 'sys_4', name: 'user.id', description: '当前用户ID', type: 'string', source: 'system' },
  { id: 'sys_5', name: 'user.name', description: '当前用户名', type: 'string', source: 'system' },
  { id: 'sys_6', name: 'timestamp.now', description: '当前时间戳（毫秒）', type: 'number', source: 'system' },
  { id: 'sys_7', name: 'timestamp.date', description: '当前日期（yyyy-MM-dd）', type: 'string', source: 'system' },
  { id: 'sys_8', name: 'env.mode', description: '运行环境模式', type: 'string', source: 'system' },
])

// ========== 自定义变量 ==========

const customVariables = ref<VariableItem[]>([])

// ========== 类型颜色映射（仅用于圆点） ==========

const typeColorMap: Record<VariableType, string> = {
  string: '#1677ff',
  number: '#52c41a',
  boolean: '#fa8c16',
  object: '#722ed1',
  array: '#eb2f96',
}

const typeLabelMap: Record<VariableType, string> = {
  string: 'string',
  number: 'number',
  boolean: 'boolean',
  object: 'object',
  array: 'array',
}

// ========== 面板控制 ==========

function openModal() {
  modalOpen.value = true
}

function closeModal() {
  modalOpen.value = false
  adding.value = false
  editingId.value = null
}

// ========== 自定义变量 CRUD ==========

function startAdd() {
  adding.value = true
  editingId.value = null
  editForm.value = { name: '', description: '', type: 'string', defaultValue: '' }
}

function cancelEdit() {
  adding.value = false
  editingId.value = null
  editForm.value = { name: '', description: '', type: 'string', defaultValue: '' }
}

function startEdit(item: VariableItem) {
  adding.value = false
  editingId.value = item.id
  editForm.value = {
    name: item.name,
    description: item.description,
    type: item.type,
    defaultValue: item.defaultValue || '',
  }
}

function saveEdit() {
  const { name, description, type, defaultValue } = editForm.value
  if (!name.trim()) {
    message.warning('变量名不能为空')
    return
  }

  if (editingId.value) {
    const item = customVariables.value.find((v) => v.id === editingId.value)
    if (item) {
      item.name = name.trim()
      item.description = description.trim()
      item.type = type
      item.defaultValue = defaultValue.trim() || undefined
    }
    editingId.value = null
  } else {
    customVariables.value.push({
      id: `cust_${Date.now()}`,
      name: name.trim(),
      description: description.trim(),
      type,
      defaultValue: defaultValue.trim() || undefined,
      source: 'custom',
    })
    adding.value = false
  }
  editForm.value = { name: '', description: '', type: 'string', defaultValue: '' }
}

function removeVariable(id: string) {
  customVariables.value = customVariables.value.filter((v) => v.id !== id)
  if (editingId.value === id) {
    editingId.value = null
  }
}

// ========== 键盘关闭 ==========

function handleEscape(e: KeyboardEvent) {
  if (e.key === 'Escape' && modalOpen.value) {
    // AModal 自身处理 ESC，这里做兜底
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
    @cancel="closeModal"
  >
    <div class="var-modal">
      <!-- 标签切换 -->
      <div class="modal-tabs">
        <button
          type="button"
          class="modal-tab"
          :class="{ active: activeTab === 'system' }"
          @click="activeTab = 'system'"
        >
          系统变量
        </button>
        <button
          type="button"
          class="modal-tab"
          :class="{ active: activeTab === 'custom' }"
          @click="activeTab = 'custom'"
        >
          自定义变量
        </button>
      </div>

      <!-- 系统变量列表 -->
      <div v-if="activeTab === 'system'" class="modal-body">
        <div class="var-list">
          <div
            v-for="item in systemVariables"
            :key="item.id"
            class="var-row"
          >
            <div class="var-row-top">
              <span class="var-dot" :style="{ background: typeColorMap[item.type] }" />
              <span class="var-name">{{ item.name }}</span>
              <span class="var-type-label">{{ typeLabelMap[item.type] }}</span>
            </div>
            <div class="var-row-desc">{{ item.description }}</div>
          </div>
        </div>
        <div v-if="systemVariables.length === 0" class="var-empty">
          暂无系统变量
        </div>
      </div>

      <!-- 自定义变量列表 -->
      <div v-if="activeTab === 'custom'" class="modal-body">
        <div v-if="customVariables.length > 0" class="var-list">
          <div
            v-for="item in customVariables"
            :key="item.id"
            class="var-row var-row-custom"
          >
            <div class="var-row-top">
              <span class="var-dot" :style="{ background: typeColorMap[item.type] }" />
              <span class="var-name">{{ item.name }}</span>
              <span class="var-type-label">{{ typeLabelMap[item.type] }}</span>
              <div class="var-row-actions">
                <button type="button" class="var-row-btn" title="编辑" @click="startEdit(item)">
                  <EditOutlined />
                </button>
                <button type="button" class="var-row-btn var-row-btn-del" title="删除" @click="removeVariable(item.id)">
                  <DeleteOutlined />
                </button>
              </div>
            </div>
            <div class="var-row-desc">{{ item.description }}</div>
            <div v-if="item.defaultValue !== undefined" class="var-row-default">
              默认值：<code>{{ item.defaultValue }}</code>
            </div>
          </div>
        </div>

        <div v-if="customVariables.length === 0 && !adding && !editingId" class="var-empty">
          暂无自定义变量，点击下方按钮添加
        </div>

        <!-- 编辑/新增表单 -->
        <div v-if="adding || editingId" class="var-form">
          <div class="var-form-grid">
            <div class="var-form-item">
              <label class="var-form-label">变量名</label>
              <AInput v-model:value="editForm.name" placeholder="如 myVar" size="small" />
            </div>
            <div class="var-form-item">
              <label class="var-form-label">类型</label>
              <ASelect
                v-model:value="editForm.type"
                size="small"
                style="width: 100%"
                :options="[
                  { value: 'string', label: 'string' },
                  { value: 'number', label: 'number' },
                  { value: 'boolean', label: 'boolean' },
                  { value: 'object', label: 'object' },
                  { value: 'array', label: 'array' },
                ]"
              />
            </div>
            <div class="var-form-item var-form-item-wide">
              <label class="var-form-label">说明</label>
              <AInput v-model:value="editForm.description" placeholder="变量用途说明" size="small" />
            </div>
            <div class="var-form-item var-form-item-wide">
              <label class="var-form-label">默认值</label>
              <AInput v-model:value="editForm.defaultValue" placeholder="可选" size="small" />
            </div>
          </div>
          <div class="var-form-footer">
            <AButton size="small" @click="cancelEdit">取消</AButton>
            <AButton size="small" type="primary" @click="saveEdit">
              <template #icon><CheckOutlined /></template>
              {{ editingId ? '保存' : '添加' }}
            </AButton>
          </div>
        </div>

        <!-- 添加按钮 -->
        <div v-if="!adding && !editingId" class="var-add-area">
          <AButton type="dashed" size="small" block @click="startAdd">
            <template #icon><PlusOutlined /></template>
            添加自定义变量
          </AButton>
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

// ========== 标签切换 ==========
.modal-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-tab {
  padding: 6px 16px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #8c8c8c;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: -1px;

  &:hover {
    color: #434343;
  }

  &.active {
    color: #1677ff;
    border-bottom-color: #1677ff;
  }
}

// ========== 主体内容 ==========
.modal-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

// ========== 变量列表 ==========
.var-list {
  display: flex;
  flex-direction: column;
}

.var-row {
  padding: 10px 0;
  border-bottom: 1px solid #fafafa;

  &:last-child {
    border-bottom: none;
  }
}

.var-row-top {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.var-dot {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.var-name {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.var-type-label {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 12px;
  color: #bfbfbf;
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
}

.var-row-desc {
  margin-top: 4px;
  padding-left: 15px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.4;
}

.var-row-default {
  margin-top: 2px;
  padding-left: 15px;
  font-size: 11px;
  color: #bfbfbf;

  code {
    padding: 1px 5px;
    border-radius: 3px;
    background: #f5f5f5;
    font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
    font-size: 11px;
  }
}

.var-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  color: #bfbfbf;
  font-size: 13px;
}

// ========== 自定义变量行操作按钮 ==========
.var-row-actions {
  display: flex;
  gap: 2px;
  margin-left: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.var-row-custom:hover .var-row-actions {
  opacity: 1;
}

.var-row-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #8c8c8c;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    color: #1677ff;
    background: #e6f4ff;
  }

  &.var-row-btn-del:hover {
    color: #ff4d4f;
    background: #fff1f0;
  }
}

// ========== 编辑表单 ==========
.var-form {
  margin-top: 12px;
  padding: 14px;
  border: 1px solid #e6f4ff;
  border-radius: 8px;
  background: #fafcff;
}

.var-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 12px;
}

.var-form-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.var-form-item-wide {
  grid-column: 1 / -1;
}

.var-form-label {
  font-size: 12px;
  font-weight: 500;
  color: #595959;
}

.var-form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

// ========== 添加按钮 ==========
.var-add-area {
  margin-top: 12px;
}
</style>

<style>
/* 全局样式：弹窗内 ant 组件微调 */
.var-modal .ant-input-sm {
  font-size: 12px;
}

.var-modal .ant-select-sm {
  font-size: 12px;
}
</style>

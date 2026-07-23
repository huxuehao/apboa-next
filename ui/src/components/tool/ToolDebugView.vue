/**
 * 工具全屏调试视图
 * 参考 McpDebugView 实现，独立文件不影响 MCP 侧代码；
 * 参数表单直接复用 McpDebugForm（纯 JSON Schema 渲染组件，无 MCP 耦合），
 * 工具的 inputSchema 为数组结构，由本组件适配为 JSON Schema 后交给表单渲染。
 *
 * @author vaulka
 */
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  LoadingOutlined,
  CheckCircleFilled,
  ClearOutlined,
  ClockCircleOutlined,
  CloseCircleFilled,
  DeleteOutlined,
  PlayCircleOutlined,
  SwapOutlined
} from '@ant-design/icons-vue'
import type { ToolDebugHistoryItem, ToolDebugResultVO, ToolVO } from '@/types'
import * as toolApi from '@/api/tool'
import { useToolDebugHistory } from '@/composables/useToolDebugHistory'
import McpDebugForm from '@/components/mcp/McpDebugForm.vue'

const props = defineProps<{
  visible: boolean
  tool: ToolVO | null
  tools: ToolVO[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  exit: []
}>()

// 调试表单参数
const formParams = ref<Record<string, unknown>>({})
// 当前选中的工具
const currentTool = ref<ToolVO | null>(null)
// 执行状态
const executing = ref(false)
// 当前调用结果
const callResult = ref<ToolDebugResultVO | null>(null)
// 选中的历史记录
const selectedHistory = ref<ToolDebugHistoryItem | null>(null)

const { historyList, addHistory, removeHistory, clearHistory } = useToolDebugHistory()

/** 工具 inputSchema 数组项结构（与 ToolForm 的 InputSchemaItem 对齐） */
interface ToolSchemaItem {
  name: string
  type?: string
  description?: string
  defaultValue?: string
  required?: boolean
}

/**
 * 工具 inputSchema（数组结构）适配为标准 JSON Schema，
 * 供 McpDebugForm 按 properties/required 动态渲染
 */
function toolSchemaToJsonSchema(items: unknown): Record<string, unknown> | null {
  if (!Array.isArray(items) || items.length === 0) return null
  const properties: Record<string, unknown> = {}
  const required: string[] = []
  for (const item of items as ToolSchemaItem[]) {
    if (!item?.name) continue
    properties[item.name] = {
      type: item.type || 'string',
      description: item.description || '',
      ...(item.defaultValue !== undefined && item.defaultValue !== '' ? { default: item.defaultValue } : {})
    }
    if (item.required) required.push(item.name)
  }
  return { type: 'object', properties, required }
}

/** 当前工具适配后的 JSON Schema */
const currentSchema = computed(() =>
  toolSchemaToJsonSchema(currentTool.value?.inputSchema ?? null)
)

/** 可用工具列表（排除已禁用的） */
const availableTools = computed(() => props.tools.filter(t => t.enabled))

/** 工具选择选项 */
const toolOptions = computed(() =>
  availableTools.value.map(t => ({ label: t.name, value: String(t.id) }))
)

/** 当前选中工具的 ID */
const selectedToolId = computed({
  get: () => currentTool.value ? String(currentTool.value.id) : undefined,
  set: (val: string | undefined) => {
    if (!val) return
    const found = availableTools.value.find(t => String(t.id) === val)
    if (found) switchTool(found)
  }
})

/** 切换工具 */
function switchTool(tool: ToolVO) {
  currentTool.value = tool
  formParams.value = {}
  callResult.value = null
  selectedHistory.value = null
}

/** 执行调试调用 */
async function handleExecute() {
  if (!currentTool.value) return
  if (executing.value) return

  executing.value = true
  callResult.value = null
  selectedHistory.value = null

  try {
    const res = await toolApi.debugTool(currentTool.value.id, formParams.value)
    const result = res.data.data
    callResult.value = result

    // 添加到历史
    addHistory({
      toolId: currentTool.value.id,
      toolName: currentTool.value.name,
      category: currentTool.value.category || '',
      input: { ...formParams.value },
      result
    })
  } catch (e: unknown) {
    // 响应拦截器对业务错误 reject 的是后端 msg 字符串，优先透出
    const errMsg = typeof e === 'string' ? e : (e instanceof Error ? e.message : '请求失败')
    const errorResult: ToolDebugResultVO = {
      success: false,
      toolName: currentTool.value.name,
      content: null,
      errorMessage: errMsg,
      durationMs: 0,
      executedAt: new Date().toISOString()
    }
    callResult.value = errorResult

    addHistory({
      toolId: currentTool.value.id,
      toolName: currentTool.value.name,
      category: currentTool.value.category || '',
      input: { ...formParams.value },
      result: errorResult
    })
  } finally {
    executing.value = false
  }
}

/** 选中历史记录 */
function selectHistory(item: ToolDebugHistoryItem) {
  selectedHistory.value = item
}

/** 返回调试面板 */
function backToDebug() {
  selectedHistory.value = null
}

/** 格式化时间为简短格式 */
function formatTime(dateStr: string): string {
  try {
    const d = new Date(dateStr)
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return dateStr
  }
}

/**
 * 格式化结果为 JSON 字符串（展示形态对齐 McpDebugView）。
 * 与 MCP 的差异：MCP 后端保证 content 是结构化 JsonNode，而工具可能直接返回 JSON 字符串，
 * 这里先尝试解析再格式化，避免整段 JSON 带转义挤在一行。
 */
function formatResultContent(result: ToolDebugResultVO): string {
  if (result.content == null) return 'null'
  let value = result.content
  if (typeof value === 'string') {
    const s = value.trim()
    if ((s.startsWith('{') && s.endsWith('}')) || (s.startsWith('[') && s.endsWith(']'))) {
      try { value = JSON.parse(s) } catch { /* 非法 JSON，按原文展示 */ }
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

/** 退出调试 */
function handleExit() {
  emit('update:visible', false)
  emit('exit')
}

/** 处理清空历史 */
function handleClearHistory() {
  clearHistory()
  selectedHistory.value = null
  message.success('调试历史已清空')
}

/** ESC 键退出 */
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    handleExit()
  }
}

/** visible 变化时初始化 */
watch(() => props.visible, (val) => {
  if (val) {
    if (props.tool) {
      switchTool(props.tool)
    } else if (availableTools.value.length > 0) {
      switchTool(availableTools.value[0] as ToolVO)
    }
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
    callResult.value = null
    selectedHistory.value = null
  }
})

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="tool-debug">
      <div v-if="visible" class="tool-debug-overlay" @keydown.esc="handleExit">
        <!-- 顶部工具栏 -->
        <header class="tool-debug-header">
          <div class="header-left">
            <AButton type="text" class="exit-btn" @click="handleExit">
              <template #icon><ArrowLeftOutlined /></template>
              退出工具调试面板
            </AButton>
          </div>
          <div class="header-center">
            <span class="header-title">工具调试</span>
          </div>
          <div class="header-right">
            <ASelect
              v-model:value="selectedToolId"
              :options="toolOptions"
              placeholder="选择工具"
              style="width: 200px"
              :disabled="executing"
            >
              <template #prefixIcon><SwapOutlined /></template>
            </ASelect>
          </div>
        </header>

        <!-- 主体区域 -->
        <div class="tool-debug-body">
          <!-- 左侧历史边栏 -->
          <aside class="debug-sidebar">
            <div class="sidebar-header">
              <div class="sidebar-title">
                <ClockCircleOutlined />
                <span>调试历史</span>
              </div>
              <AButton
                v-if="historyList.length > 0"
                type="text"
                size="small"
                danger
                @click="handleClearHistory"
              >
                <template #icon><DeleteOutlined /></template>
              </AButton>
            </div>
            <div class="sidebar-list">
              <TransitionGroup name="history-item" tag="div">
                <div
                  v-for="item in historyList"
                  :key="item.id"
                  class="history-item"
                  :class="{ active: selectedHistory?.id === item.id }"
                  @click="selectHistory(item)"
                >
                  <div class="history-item-icon">
                    <CheckCircleFilled v-if="item.result.success" class="icon-success" />
                    <CloseCircleFilled v-else class="icon-fail" />
                  </div>
                  <div class="history-item-content">
                    <div class="history-item-name">{{ item.toolName }}</div>
                    <div class="history-item-meta">
                      {{ formatTime(item.executedAt) }}
                      <span v-if="item.result.durationMs" class="history-item-duration">
                        {{ item.result.durationMs }}ms
                      </span>
                    </div>
                  </div>
                  <AButton
                    type="text"
                    size="small"
                    class="history-item-delete"
                    @click.stop="removeHistory(item.id)"
                  >
                    <template #icon><ClearOutlined /></template>
                  </AButton>
                </div>
              </TransitionGroup>
              <div v-if="!historyList.length" class="sidebar-empty">
                <ClockCircleOutlined class="empty-icon" />
                <span class="text-placeholder text-xs">暂无调试记录</span>
              </div>
            </div>
          </aside>

          <!-- 右侧调试面板 -->
          <main class="debug-panel">
            <Transition name="panel-fade" mode="out-in">
              <!-- 历史详情视图 -->
              <div v-if="selectedHistory" key="history" class="split-layout">
                <div class="split-left">
                  <div class="history-detail-header">
                    <AButton type="text" style="border: 1px solid #E4E5E7;" @click="backToDebug">
                      <template #icon><ArrowLeftOutlined /></template>
                      返回调试
                    </AButton>
                  </div>
                  <div class="history-detail-section">
                    <div class="section-label">工具名称</div>
                    <div class="section-value">{{ selectedHistory.toolName }}</div>
                  </div>
                  <div class="history-detail-section">
                    <div class="section-label">调用参数</div>
                    <pre class="result-code">{{ JSON.stringify(selectedHistory.input, null, 2) || '{}' }}</pre>
                  </div>
                </div>
                <div class="split-right">
                  <div class="result-section">
                    <div class="result-header">
                      <span class="result-label">调用结果</span>
                      <ATag :color="selectedHistory.result.success ? 'success' : 'error'" :bordered="false">
                        {{ selectedHistory.result.success ? '成功' : '失败' }}
                      </ATag>
                      <span v-if="selectedHistory.result.durationMs" class="result-duration text-placeholder text-xs">
                        {{ selectedHistory.result.durationMs }}ms
                      </span>
                    </div>
                    <pre
                      class="result-code result-code-full"
                      :class="{ 'result-error': !selectedHistory.result.success }"
                    >{{ selectedHistory.result.success
                      ? formatResultContent(selectedHistory.result)
                      : selectedHistory.result.errorMessage }}</pre>
                  </div>
                </div>
              </div>

              <!-- 调试表单视图 -->
              <div v-else key="debug" class="split-layout">
                <!-- 左侧：工具信息 + 参数表单 + 执行按钮 -->
                <div class="split-left">
                  <div v-if="currentTool" class="tool-info-card">
                    <div class="tool-info-name">{{ currentTool.name }}</div>
                    <div class="tool-info-desc text-placeholder">
                      {{ currentTool.description || '暂无描述' }}
                    </div>
                  </div>

                  <div class="debug-form-section">
                    <McpDebugForm
                      v-model="formParams"
                      :schema="currentSchema"
                    />
                  </div>

                  <div class="debug-actions">
                    <AButton
                      type="primary"
                      :loading="executing"
                      :disabled="!currentTool"
                      @click="handleExecute"
                    >
                      <template #icon><PlayCircleOutlined /></template>
                      {{ executing ? '执行中...' : '执行调试' }}
                    </AButton>
                  </div>
                </div>

                <!-- 右侧：执行结果 -->
                <div class="split-right">
                  <div v-if="callResult" class="result-section">
                    <div class="result-header">
                      <span class="result-label">调用结果</span>
                      <ATag :color="callResult.success ? 'success' : 'error'" :bordered="false">
                        {{ callResult.success ? '成功' : '失败' }}
                      </ATag>
                      <span v-if="callResult.durationMs" class="result-duration text-placeholder text-xs">
                        {{ callResult.durationMs }}ms
                      </span>
                    </div>
                    <pre
                      class="result-code result-code-full"
                      :class="{ 'result-error': !callResult.success }"
                    >{{ callResult.success
                      ? formatResultContent(callResult)
                      : callResult.errorMessage }}</pre>
                  </div>
                  <div v-else class="result-placeholder">
                    <LoadingOutlined v-if="executing" class="result-placeholder-icon"  />
                    <PlayCircleOutlined v-else class="result-placeholder-icon" />
                    <span class="text-placeholder">
                    {{ executing ? '正在执行调试中，请稍候' : '执行调试后，结果将显示在此处' }}
                    </span>
                  </div>
                </div>
              </div>
            </Transition>
          </main>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
/* 全屏遮罩过渡动画 */
.tool-debug-enter-active {
  transition: opacity 0.28s cubic-bezier(0.22, 1, 0.36, 1);
  .tool-debug-header,
  .debug-sidebar,
  .debug-panel {
    transition: opacity 0.32s cubic-bezier(0.22, 1, 0.36, 1), transform 0.32s cubic-bezier(0.22, 1, 0.36, 1);
  }
  .tool-debug-header { transition-delay: 0.04s; }
  .debug-sidebar { transition-delay: 0.08s; }
  .debug-panel { transition-delay: 0.12s; }
}
.tool-debug-leave-active {
  transition: opacity 0.2s cubic-bezier(0.4, 0, 1, 1);
  .tool-debug-header,
  .debug-sidebar,
  .debug-panel {
    transition: opacity 0.15s cubic-bezier(0.4, 0, 1, 1), transform 0.15s cubic-bezier(0.4, 0, 1, 1);
  }
}
.tool-debug-enter-from {
  opacity: 0;
  .tool-debug-header { opacity: 0; transform: translateY(-12px); }
  .debug-sidebar { opacity: 0; transform: translateX(-16px); }
  .debug-panel { opacity: 0; transform: translateX(16px); }
}
.tool-debug-leave-to {
  opacity: 0;
  .tool-debug-header,
  .debug-sidebar,
  .debug-panel { opacity: 0; transform: translateY(8px); }
}

/* 面板内容切换动画 */
.panel-fade-enter-active { transition: opacity 0.22s ease, transform 0.22s ease; }
.panel-fade-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.panel-fade-enter-from { opacity: 0; transform: translateY(8px); }
.panel-fade-leave-to { opacity: 0; transform: translateY(-4px); }

/* 结果面板滑入动画 */
.result-slide-enter-active { transition: opacity 0.3s ease, transform 0.3s ease; }
.result-slide-leave-active { transition: opacity 0.15s ease; }
.result-slide-enter-from { opacity: 0; transform: translateY(12px); }
.result-slide-leave-to { opacity: 0; }

/* 历史列表项动画 */
.history-item-enter-active { transition: all 0.25s ease; }
.history-item-leave-active { transition: all 0.2s ease; position: absolute; }
.history-item-enter-from { opacity: 0; transform: translateX(-12px); }
.history-item-leave-to { opacity: 0; transform: translateX(12px); }
.history-item-move { transition: transform 0.25s ease; }

/* 全屏覆盖层 */
.tool-debug-overlay {
  position: fixed;
  inset: 0;
  z-index: 1050;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  color: var(--color-text-primary, #1a1a2e);
}

/* 顶部工具栏 */
.tool-debug-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  flex-shrink: 0;
  gap: 16px;
}
.header-left { flex-shrink: 0; }
.header-center {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
}
.header-right { flex-shrink: 0; }
.exit-btn { color: inherit; border: 1px solid #E4E5E7; }

/* 主体布局 */
.tool-debug-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 左侧历史边栏 */
.debug-sidebar {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(245, 247, 250, 0.5);
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  flex-shrink: 0;
}
.sidebar-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  opacity: 0.7;
}
.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
  position: relative;
}
.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 16px;
  .empty-icon { font-size: 24px; opacity: 0.25; }
}

/* 历史记录项 */
.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease;
  margin-bottom: 2px;
  position: relative;
  &:hover {
    background: rgba(15, 23, 42, 0.05);
    .history-item-delete { opacity: 1; }
  }
  &.active {
    background: rgba(22, 119, 255, 0.08);
    .history-item-name { color: #1677ff; }
  }
}
.history-item-icon { flex-shrink: 0; font-size: 14px; }
.icon-success { color: #52c41a; }
.icon-fail { color: #ff4d4f; }
.history-item-content { flex: 1; min-width: 0; }
.history-item-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-item-meta {
  font-size: 11px;
  opacity: 0.5;
  display: flex;
  align-items: center;
  gap: 4px;
}
.history-item-duration { opacity: 0.8; }
.history-item-delete {
  opacity: 0;
  flex-shrink: 0;
  transition: opacity 0.15s ease;
  font-size: 12px;
}

/* 右侧调试面板 */
.debug-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 24px 32px;
}

/* 左右分栏布局 */
.split-layout {
  display: flex;
  gap: 24px;
  height: 100%;
  min-height: 0;
}
.split-left {
  width: 50%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
  padding-right: 24px;
  border-right: 1px solid rgba(15, 23, 42, 0.06);
}
.split-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

/* 工具信息卡片 */
.tool-info-card {
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(22, 119, 255, 0.02);
}
.tool-info-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}
.tool-info-desc {
  font-size: 13px;
  line-height: 1.5;
}

/* 执行按钮区 */
.debug-actions {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}

/* 结果区域 */
.result-section {
  padding-top: 4px;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.result-label {
  font-size: 14px;
  font-weight: 600;
}
.result-duration {
  margin-left: auto;
}
.result-code {
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 8px;
  padding: 14px 16px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  &.result-error {
    background: rgba(255, 77, 79, 0.04);
    border-color: rgba(255, 77, 79, 0.2);
    color: #cf1322;
  }
}

/* 结果占位符 */
.result-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex: 1;
  min-height: 200px;
}
.result-placeholder-icon {
  font-size: 36px;
  opacity: 0.15;
}

/* 历史详情区块 */
.history-detail-header {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}
.history-detail-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.section-label {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-value {
  font-size: 14px;
}

/* 右侧全高结果代码块 */
.result-code-full {
  flex: 1;
  max-height: none;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .debug-sidebar { width: 220px; }
  .debug-panel { padding: 16px; }
  .header-center { display: none; }
}
</style>

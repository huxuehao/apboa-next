/**
 * MCP 工具调试视图
 *
 * @author huxuehao
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
import type { McpDebugHistoryItem, McpServerVO, McpToolDebugResultVO, McpToolVO } from '@/types'
import * as mcpApi from '@/api/mcp'
import { useMcpDebugHistory } from '@/composables/useMcpDebugHistory'
import McpDebugForm from '@/components/mcp/McpDebugForm.vue'

const props = defineProps<{
  visible: boolean
  tool: McpToolVO | null
  server: McpServerVO | null
  tools: McpToolVO[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  exit: []
}>()

const formParams = ref<Record<string, unknown>>({})
const currentTool = ref<McpToolVO | null>(null)
const executing = ref(false)
const callResult = ref<McpToolDebugResultVO | null>(null)
const selectedHistory = ref<McpDebugHistoryItem | null>(null)

const { historyList, addHistory, removeHistory, clearHistory } = useMcpDebugHistory()

const availableTools = computed(() => props.tools.filter(t => !t.missing && t.enabled))

const toolOptions = computed(() =>
  availableTools.value.map(t => ({ label: t.toolName, value: String(t.id) }))
)

const selectedToolId = computed({
  get: () => currentTool.value ? String(currentTool.value.id) : undefined,
  set: (val: string | undefined) => {
    if (!val) return
    const found = availableTools.value.find(t => String(t.id) === val)
    if (found) switchTool(found)
  }
})

function switchTool(tool: McpToolVO) {
  currentTool.value = tool
  formParams.value = {}
  callResult.value = null
  selectedHistory.value = null
}

async function handleExecute() {
  if (!currentTool.value || !props.server) return
  if (executing.value) return

  executing.value = true
  callResult.value = null
  selectedHistory.value = null

  try {
    const res = await mcpApi.debugTool(currentTool.value.id, formParams.value)
    const result = res.data.data
    callResult.value = result

    addHistory({
      toolId: currentTool.value.id,
      toolName: currentTool.value.toolName,
      serverName: props.server.name,
      input: { ...formParams.value },
      result
    })
  } catch (e: unknown) {
    const errMsg = e instanceof Error ? e.message : '请求失败'
    const errorResult: McpToolDebugResultVO = {
      success: false,
      toolName: currentTool.value.toolName,
      content: null,
      errorMessage: errMsg,
      durationMs: 0,
      executedAt: new Date().toISOString()
    }
    callResult.value = errorResult

    addHistory({
      toolId: currentTool.value.id,
      toolName: currentTool.value.toolName,
      serverName: props.server?.name || '',
      input: { ...formParams.value },
      result: errorResult
    })
  } finally {
    executing.value = false
  }
}

function selectHistory(item: McpDebugHistoryItem) {
  selectedHistory.value = item
}

function backToDebug() {
  selectedHistory.value = null
}

function formatTime(dateStr: string): string {
  try {
    const d = new Date(dateStr)
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return dateStr
  }
}

function formatResultContent(result: McpToolDebugResultVO): string {
  if (result.content == null) return 'null'
  try {
    return JSON.stringify(result.content, null, 2)
  } catch {
    return String(result.content)
  }
}

function handleExit() {
  emit('update:visible', false)
  emit('exit')
}

function handleClearHistory() {
  clearHistory()
  selectedHistory.value = null
  message.success('调试历史已清空')
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    handleExit()
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    if (props.tool) {
      switchTool(props.tool)
    } else if (availableTools.value.length > 0) {
      switchTool(availableTools.value[0] as McpToolVO)
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
    <Transition name="debug-fade">
      <div v-if="visible" class="debug-overlay">
        <!-- 顶部栏 -->
        <header class="debug-header">
          <AButton type="link" @click="handleExit" class="back-btn">
            <ArrowLeftOutlined />
            <span>退出调试</span>
          </AButton>
          <span class="header-title">{{ server?.name }}</span>
          <ASelect
            v-model:value="selectedToolId"
            :options="toolOptions"
            placeholder="选择工具"
            style="width: 200px"
            size="small"
            :disabled="executing"
          >
            <template #prefixIcon><SwapOutlined /></template>
          </ASelect>
        </header>

        <!-- 主体 -->
        <div class="debug-body">
          <!-- 左侧历史 -->
          <aside class="debug-sidebar">
            <div class="sidebar-head">
              <span class="sidebar-label">调试历史</span>
              <AButton
                v-if="historyList.length > 0"
                type="text"
                size="small"
                danger
                @click="handleClearHistory"
              >
                <DeleteOutlined />
              </AButton>
            </div>
            <div class="sidebar-list">
              <div
                v-for="item in historyList"
                :key="item.id"
                class="history-item"
                :class="{ active: selectedHistory?.id === item.id }"
                @click="selectHistory(item)"
              >
                <CheckCircleFilled v-if="item.result.success" class="history-icon success" />
                <CloseCircleFilled v-else class="history-icon fail" />
                <div class="history-info">
                  <div class="history-name">{{ item.toolName }}</div>
                  <div class="history-meta">
                    {{ formatTime(item.executedAt) }}
                    <span v-if="item.result.durationMs">{{ item.result.durationMs }}ms</span>
                  </div>
                </div>
                <AButton type="text" size="small" class="history-del" @click.stop="removeHistory(item.id)">
                  <ClearOutlined />
                </AButton>
              </div>
              <div v-if="!historyList.length" class="sidebar-empty">
                <ClockCircleOutlined />
                <span class="text-placeholder text-xs">暂无调试记录</span>
              </div>
            </div>
          </aside>

          <!-- 右侧内容 -->
          <main class="debug-main">
            <!-- 历史详情 -->
            <template v-if="selectedHistory">
              <div class="debug-pane">
                <div class="pane-left">
                  <AButton type="text" size="small" @click="backToDebug" style="margin-bottom: 12px;">
                    <ArrowLeftOutlined /> 返回调试
                  </AButton>
                  <div class="field-group">
                    <div class="field-label">工具名称</div>
                    <div>{{ selectedHistory.toolName }}</div>
                  </div>
                  <div class="field-group">
                    <div class="field-label">调用参数</div>
                    <pre class="code-block">{{ JSON.stringify(selectedHistory.input, null, 2) || '{}' }}</pre>
                  </div>
                </div>
                <div class="pane-right">
                  <div class="pane-head">
                    <span class="field-label">调用结果</span>
                    <ATag :color="selectedHistory.result.success ? 'default' : 'error'" :bordered="false">
                      {{ selectedHistory.result.success ? '成功' : '失败' }}
                    </ATag>
                    <span v-if="selectedHistory.result.durationMs" class="text-placeholder text-xs">
                      {{ selectedHistory.result.durationMs }}ms
                    </span>
                  </div>
                  <pre
                    class="code-block code-full"
                    :class="{ 'code-error': !selectedHistory.result.success }"
                  >{{ selectedHistory.result.success
                    ? formatResultContent(selectedHistory.result)
                    : selectedHistory.result.errorMessage }}</pre>
                </div>
              </div>
            </template>

            <!-- 调试表单 -->
            <template v-else>
              <div class="debug-pane">
                <div class="pane-left">
                  <div v-if="currentTool" class="tool-brief">
                    <div class="tool-brief-name">{{ currentTool.toolName }}</div>
                    <div class="tool-brief-desc text-placeholder">{{ currentTool.description || '暂无描述' }}</div>
                  </div>
                  <McpDebugForm
                    v-model="formParams"
                    :schema="currentTool?.inputSchema ?? null"
                  />
                  <AButton
                    type="primary"
                    :loading="executing"
                    :disabled="!currentTool"
                    @click="handleExecute"
                    style="margin-top: 12px;"
                  >
                    <PlayCircleOutlined />
                    {{ executing ? '执行中...' : '执行调试' }}
                  </AButton>
                </div>
                <div class="pane-right">
                  <template v-if="callResult">
                    <div class="pane-head">
                      <span class="field-label">调用结果</span>
                      <ATag :color="callResult.success ? 'default' : 'error'" :bordered="false">
                        {{ callResult.success ? '成功' : '失败' }}
                      </ATag>
                      <span v-if="callResult.durationMs" class="text-placeholder text-xs">
                        {{ callResult.durationMs }}ms
                      </span>
                    </div>
                    <pre
                      class="code-block code-full"
                      :class="{ 'code-error': !callResult.success }"
                    >{{ callResult.success
                      ? formatResultContent(callResult)
                      : callResult.errorMessage }}</pre>
                  </template>
                  <div v-else class="result-empty">
                    <LoadingOutlined v-if="executing" />
                    <PlayCircleOutlined v-else />
                    <span class="text-placeholder">{{ executing ? '正在执行...' : '执行调试后，结果将显示在此处' }}</span>
                  </div>
                </div>
              </div>
            </template>
          </main>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
/* 过渡动画 - 单一淡入淡出 */
.debug-fade-enter-active,
.debug-fade-leave-active {
  transition: opacity var(--transition-base);
}
.debug-fade-enter-from,
.debug-fade-leave-to {
  opacity: 0;
}

/* 全屏覆盖层 */
.debug-overlay {
  position: fixed;
  inset: 0;
  z-index: 1050;
  display: flex;
  flex-direction: column;
  background: #fff;
}

/* 顶部栏 */
.debug-header {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 48px;
  padding: 0 var(--spacing-md);
  border-bottom: 1px solid #ebebeb;
  flex-shrink: 0;

  .back-btn {
    padding: 0;
    color: rgba(0, 0, 0, 0.45);
    flex-shrink: 0;

    &:hover {
      color: rgba(0, 0, 0, 0.88);
    }

    span {
      margin-left: 4px;
    }
  }

  .header-title {
    flex: 1;
    font-size: var(--font-size-base);
    font-weight: 600;
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* 主体布局 */
.debug-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* 左侧历史边栏 */
.debug-sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebebeb;
  background: #fafafa;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 8px;
  flex-shrink: 0;
}

.sidebar-label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-regular);
}

.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}

.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 16px;
  color: var(--color-text-placeholder);
  font-size: 24px;
}

/* 历史记录项 */
.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background var(--transition-base);

  &:hover {
    background: rgba(0, 0, 0, 0.04);

    .history-del {
      opacity: 1;
    }
  }

  &.active {
    background: rgba(22, 119, 255, 0.06);
  }
}

.history-icon {
  flex-shrink: 0;
  font-size: 14px;

  &.success {
    color: #1677ff;
  }

  &.fail {
    color: #8c8c8c;
  }
}

.history-info {
  flex: 1;
  min-width: 0;
}

.history-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-meta {
  font-size: 11px;
  color: var(--color-text-placeholder);
  display: flex;
  align-items: center;
  gap: 6px;
}

.history-del {
  opacity: 0;
  flex-shrink: 0;
  transition: opacity var(--transition-base);
  font-size: 12px;
  color: var(--color-text-placeholder);
}

/* 右侧主内容区 */
.debug-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: var(--spacing-md);
}

.debug-pane {
  display: flex;
  gap: 24px;
  height: 100%;
}

.pane-left {
  width: 50%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 24px;
  border-right: 1px solid #ebebeb;
}

.pane-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.pane-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

/* 工具简介卡片 */
.tool-brief {
  padding: 12px 16px;
  border-radius: var(--border-radius-lg);
  border: 1px solid #ebebeb;
  background: #fff;
}

.tool-brief-name {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.tool-brief-desc {
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

/* 代码块 */
.code-block {
  background: #fafafa;
  border: 1px solid #ebebeb;
  border-radius: var(--border-radius-lg);
  padding: 12px 16px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;

  &.code-full {
    flex: 1;
    max-height: none;
  }

  &.code-error {
    background: #fff2f0;
    border-color: #ffccc7;
    color: #820014;
  }
}

/* 结果空态 */
.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
  min-height: 200px;
  font-size: 32px;
  color: var(--color-text-placeholder);
}

/* 响应式 */
@media (max-width: 768px) {
  .debug-sidebar {
    width: 200px;
  }

  .debug-main {
    padding: var(--spacing-sm);
  }

  .debug-pane {
    flex-direction: column;
  }

  .pane-left {
    width: 100%;
    padding-right: 0;
    padding-bottom: 16px;
    border-right: none;
    border-bottom: 1px solid #ebebeb;
  }
}
</style>

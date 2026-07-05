<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CloseOutlined, PlayCircleOutlined, BugOutlined, ReloadOutlined, CopyOutlined, CheckCircleFilled, CloseCircleFilled, PlayCircleFilled, ExclamationCircleFilled } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import type { WorkflowFlowNode, WorkflowNodeExecution, WorkflowRunRequest, WorkflowRunResult } from '@/types/workflow'

const props = defineProps<{
  open: boolean
  result: WorkflowRunResult | null
  nodes: WorkflowFlowNode[]
  loading?: boolean
}>()

const inputText = defineModel<string>('inputText', { default: '{\n  "params": [],\n  "variables": {}\n}' })
const width = defineModel<number>('width', { default: 442 })

const emit = defineEmits<{
  run: []
  close: []
  focusNode: [nodeId: string]
}>()

const activeKey = ref('input')
const maxWidth = computed(() => Math.floor(window.innerWidth * 0.55))
const dragging = ref(false)
const paramValues = ref<Record<string, unknown>>({})
const variablesText = ref('{}')

const startNode = computed(() => props.nodes.find((node) => node.data.type === 'START') || null)
const startParams = computed<Array<Record<string, unknown>>>(() => {
  const params = startNode.value?.data.config?.params
  return Array.isArray(params) ? params as Array<Record<string, unknown>> : []
})
const hasStartParams = computed(() => startParams.value.length > 0)
const runStatus = computed(() => props.result?.run?.status)
const finalOutput = computed(() => props.result?.output ?? props.result?.run?.outputs ?? null)
const inputIssues = computed(() => collectInputIssues())

watch(
  startParams,
  () => resetFromStartParams(),
  { immediate: true, deep: true },
)

watch([paramValues, variablesText], syncInputText, { deep: true })

watch(
  () => props.result?.run?.status,
  () => {
    activeKey.value = 'result'
  }, {
    deep: true
  }
)

function resetFromStartParams() {
  const next: Record<string, unknown> = {}
  startParams.value.forEach((param) => {
    const name = String(param.name || '')
    if (!name) return
    next[name] = param.value ?? defaultValueByType(String(param.type || 'String'))
  })
  paramValues.value = next
  syncInputText()
}

function defaultValueByType(type: string) {
  if (type === 'Boolean') return false
  if (['Long', 'Integer', 'Float', 'Double'].includes(type)) return ''
  if (type === 'Array') return '[]'
  if (type === 'Object') return '{}'
  return ''
}

function syncInputText() {
  let variables: Record<string, unknown> = {}
  try {
    variables = JSON.parse(variablesText.value || '{}')
  } catch {
    variables = {}
  }
  const payload: WorkflowRunRequest = {
    params: startParams.value
      .map((param) => {
        const name = String(param.name || '')
        return name ? { name, value: normalizeValue(paramValues.value[name], String(param.type || 'String')) } : null
      })
      .filter(Boolean) as Array<{ name: string; value: unknown }>,
    variables,
  }
  inputText.value = JSON.stringify(payload, null, 2)
}

function collectInputIssues() {
  const issues: string[] = []
  startParams.value.forEach((param) => {
    const name = String(param.name || '')
    if (!name) return
    const type = String(param.type || 'String')
    const value = paramValues.value[name]
    if (param.required && isEmptyValue(value)) {
      issues.push(`${name} 为必填参数`)
      return
    }
    if (isEmptyValue(value)) return
    if (type === 'Object' || type === 'Array') {
      try {
        const parsed = typeof value === 'string' ? JSON.parse(value) : value
        if (type === 'Array' && !Array.isArray(parsed)) {
          issues.push(`${name} 必须是 JSON 数组`)
        }
        if (type === 'Object' && (Array.isArray(parsed) || typeof parsed !== 'object' || parsed === null)) {
          issues.push(`${name} 必须是 JSON 对象`)
        }
      } catch {
        issues.push(`${name} 不是合法 JSON`)
      }
    }
    if (['Long', 'Integer', 'Float', 'Double'].includes(type) && !Number.isFinite(Number(value))) {
      issues.push(`${name} 必须是数字`)
    }
  })
  try {
    JSON.parse(variablesText.value || '{}')
  } catch {
    issues.push('全局变量不是合法 JSON')
  }
  try {
    JSON.parse(inputText.value || '{}')
  } catch {
    issues.push('高级 JSON 不是合法 JSON')
  }
  return issues
}

function isEmptyValue(value: unknown) {
  return value === undefined || value === null || (typeof value === 'string' && value.trim() === '')
}

function runWithValidation() {
  if (inputIssues.value.length) {
    activeKey.value = 'input'
    message.warning(inputIssues.value[0])
    return
  }
  emit('run')
}

function normalizeValue(value: unknown, type: string) {
  if (type === 'Boolean') return Boolean(value)
  if (type === 'Array' || type === 'Object') return typeof value === 'string' ? value : JSON.stringify(value ?? (type === 'Array' ? [] : {}))
  return value == null ? '' : String(value)
}

function formatJson(value: unknown) {
  if (value === undefined || value === null || value === '') return '-'
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  return JSON.stringify(value, null, 2)
}

function duration(start?: number, end?: number) {
  if (!start || !end) return '-'
  return `${Math.max(0, end - start)} ms`
}

const copied = ref(false)

function statusText(status?: string) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAIL') return '失败'
  if (status === 'RUNNING') return '运行中'
  return '-'
}

async function copyOutput() {
  let text = ''
  if (props.result?.run?.error) {
    text = props.result.run.error
  } else {
    text = formatJson(finalOutput.value ?? props.result?.run)
  }

  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    message.error('复制失败')
  }
}

function executionTitle(item: WorkflowNodeExecution, index: number) {
  return `${index + 1}. ${item.nodeTitle || item.nodeId}`
}

function beginResize(event: MouseEvent) {
  dragging.value = true
  const startX = event.clientX
  const startWidth = width.value
  const onMove = (moveEvent: MouseEvent) => {
    const next = startWidth + (startX - moveEvent.clientX)
    width.value = Math.max(442, Math.min(maxWidth.value, next))
  }
  const onUp = () => {
    dragging.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
</script>

<template>
  <section v-if="open" class="run-dock" :class="{ dragging }" :style="{ width: `${width}px` }">
    <div class="resize-handle" @mousedown.prevent="beginResize" />

    <header class="dock-header">
      <div>
        <div class="dock-title"><BugOutlined /> 调试面板</div>
        <div class="dock-subtitle">使用当前草稿配置运行，不影响已发布版本</div>
      </div>
      <div class="dock-actions">
        <AButton type="text" @click="emit('close')">
          <template #icon><CloseOutlined /></template>
        </AButton>
      </div>
    </header>

    <ATabs v-model:active-key="activeKey" size="small" class="dock-tabs">
      <ATabPane key="input" tab="调试输入">
        <div class="debug-input">
          <div class="debug-scroll">
            <AAlert
              v-if="inputIssues.length"
              type="warning"
              style="margin-bottom: 8px;"
              show-icon banner closable
              :message="inputIssues[0]"
            />
            <div class="debug-section-head">
              <div>
                <div class="debug-section-title">开始节点参数</div>
                <div class="debug-section-desc">根据开始节点的输入配置自动生成，修改后立即生效。</div>
              </div>
              <AButton type="text" size="small" @click="resetFromStartParams">
                <template #icon><ReloadOutlined /></template>
                重置
              </AButton>
            </div>
            <div v-if="hasStartParams" class="param-list">
              <div v-for="param in startParams" :key="String(param.name)" class="param-card">
                <div class="param-header">
                  <span class="param-name" :class="{ 'required-field_end': param.required }">{{ param.name }}</span>
                  <span>{{ param.type || 'String' }}</span>
                </div>
                <ASwitch
                  v-if="param.type === 'Boolean'"
                  v-model:checked="paramValues[String(param.name)]"
                  size="small"
                />
                <AInput
                  v-else-if="param.type !== 'Object' && param.type !== 'Array'"
                  v-model:value="paramValues[String(param.name)]"
                  :placeholder="String(param.remark || '请输入调试值')"
                />
                <ATextarea
                  v-else
                  v-model:value="paramValues[String(param.name)]"
                  :auto-size="{ minRows: 2, maxRows: 5 }"
                  placeholder="请输入 JSON"
                />
                <div v-if="param.remark" class="param-remark">{{ param.remark }}</div>
              </div>
            </div>
            <AEmpty v-else description="开始节点未定义输入参数" />

            <div class="debug-section-title mt">全局变量</div>
            <div class="debug-section-desc" style="margin-top: 2px;">定义可在工作流中引用的变量，格式为 JSON 对象。</div>
            <ConfigCodeEditor
              :model-value="variablesText"
              language="json"
              height="90px"
              :maximize="false"
              @update:model-value="(v: string) => variablesText = v"
            />
          </div>
          <div class="debug-footer">
            <AButton type="primary" :loading="loading" block @click="runWithValidation">
              <template #icon><PlayCircleOutlined /></template>
              运行
            </AButton>
          </div>
        </div>
      </ATabPane>

      <ATabPane key="result" tab="运行结果">
        <div class="result-scroll">
          <template v-if="result">
            <div class="result-summary">
              <div class="result-card">
                <span class="result-label">状态</span>
                <ATooltip :title="statusText(runStatus)">
                  <CheckCircleFilled v-if="runStatus === 'SUCCESS'" style="color: #52c41a; font-size: 16px;" />
                  <CloseCircleFilled v-else-if="runStatus === 'FAIL'" style="color: #ff4d4f; font-size: 16px;" />
                  <PlayCircleFilled v-else-if="runStatus === 'RUNNING'" style="color: #1677ff; font-size: 16px;" />
                  <ExclamationCircleFilled v-else style="color: #8c8c8c; font-size: 16px;" />
                </ATooltip>
              </div>
              <div class="result-card">
                <span class="result-label">耗时</span>
                <span>{{ duration(result?.run?.startTime, result?.run?.endTime) }}</span>
              </div>
              <div v-if="result?.nodeExecutions?.length" class="result-card">
                <span class="result-label">节点</span>
                <span>{{ result.nodeExecutions.length }}</span>
              </div>
            </div>

            <div class="result-output-head">
              <span class="result-output-title">输出结果</span>
              <AButton type="text" size="small" @click="copyOutput">
                <template #icon><CopyOutlined /></template>
                {{ copied ? '已复制' : '复制' }}
              </AButton>
            </div>
            <pre class="json-pre">{{ result?.run?.error || formatJson(finalOutput ?? result?.run) }}</pre>
          </template>

          <AEmpty v-else description="暂无运行结果，点击「运行」按钮开始调试" />
        </div>
      </ATabPane>

      <ATabPane key="nodes" tab="节点日志">
        <div class="execution-scroll">
          <div v-if="result?.nodeExecutions?.length" class="execution-list">
            <div
              v-for="(item, index) in result.nodeExecutions"
              :key="item.id || `${item.nodeId}-${index}`"
              class="execution-item"
            >
              <div class="execution-head" @click="emit('focusNode', item.nodeId)">
                <div class="execution-copy">
                  <span class="execution-title">{{ executionTitle(item, index) }}</span>
                  <span class="execution-meta">{{ item.nodeType }} · {{ duration(item.startTime, item.endTime) }}</span>
                </div>
                <ATooltip :title="statusText(item.status)">
                  <CheckCircleFilled v-if="item.status === 'SUCCESS'" style="color: #52c41a; font-size: 16px;" />
                  <CloseCircleFilled v-else-if="item.status === 'FAIL'" style="color: #ff4d4f; font-size: 16px;" />
                  <PlayCircleFilled v-else-if="item.status === 'RUNNING'" style="color: #1677ff; font-size: 16px;" />
                  <ExclamationCircleFilled v-else style="color: #8c8c8c; font-size: 16px;" />
                </ATooltip>
              </div>
              <ACollapse ghost size="small">
                <ACollapsePanel v-if="item.error  && item.error !== '{}'" key="error" header="错误">
                  <pre class="json-pre compact">{{ formatJson(item.error) }}</pre>
                </ACollapsePanel>
                <ACollapsePanel key="inputs" header="输入">
                  <pre class="json-pre compact">{{ formatJson(item.inputs) }}</pre>
                </ACollapsePanel>
                <ACollapsePanel key="process" header="处理">
                  <pre class="json-pre compact">{{ formatJson(item.processData) }}</pre>
                </ACollapsePanel>
                <ACollapsePanel key="outputs" header="输出">
                  <pre class="json-pre compact">{{ formatJson(item.outputs) }}</pre>
                </ACollapsePanel>
              </ACollapse>
            </div>
          </div>
          <AEmpty v-else description="暂无节点日志" />
        </div>
      </ATabPane>
    </ATabs>
  </section>
</template>

<style scoped lang="scss">
.run-dock {
  position: absolute;
  right: 16px;
  top: 60px;
  bottom: 18px;
  z-index: 16;
  min-width: 442px;
  max-width: 55vw;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.resize-handle {
  position: absolute;
  left: -5px;
  top: 0;
  bottom: 0;
  width: 3px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s ease;
}

.resize-handle::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 40px;
  border-radius: 1px;
  background: #c9c9c9;
  transition: background 0.2s ease;
}

.resize-handle:hover::after,
.dragging .resize-handle::after {
  display: none;
}

.resize-handle:hover,
.dragging .resize-handle {
  background: #1677FF;
}

.dock-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
}

.dock-title {
  color: #262626;
  font-size: 15px;
  font-weight: 700;
}

.dock-subtitle,
.debug-section-desc,
.param-remark,
.execution-meta {
  color: #8c8c8c;
  font-size: 12px;
}

.dock-actions,
.debug-section-head,
.result-summary,
.execution-head,
.param-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dock-actions,
.debug-section-head,
.execution-head {
  justify-content: space-between;
}

.dock-tabs {
  min-height: 0;
  padding: 0 14px;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  :deep(.ant-tabs-nav) {
    margin-bottom: 8px;
    flex-shrink: 0;
  }

  :deep(.ant-tabs-content-holder) {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  :deep(.ant-tabs-content) {
    height: 100%;
    overflow: hidden;
    padding: 0;
  }

  :deep(.ant-tabs-tabpane) {
    height: 100%;
  }
}

.debug-input,
.param-list,
.execution-list {
  display: grid;
  gap: 10px;
}

.debug-input {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.debug-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.result-scroll,
.execution-scroll {
  height: calc(100% - 16px);
  overflow: auto;
}

.debug-footer {
  flex-shrink: 0;
  padding-bottom: 16px;
}

.debug-section-title {
  color: #262626;
  font-size: 13px;
  font-weight: 700;
}

.debug-section-title.mt {
  margin-top: 10px;
}

.param-card {
  display: grid;
  gap: 8px;
  padding: 6px 12px 12px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.param-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.param-name {
  flex: 1;
  min-width: 0;
  color: #262626;
  font-weight: 600;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-summary {
  margin-bottom: 10px;
}

.result-card {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  background: #F2F4F7;
}

.result-label {
  color: #8c8c8c;
  font-size: 12px;
}

.result-error {
  margin-bottom: 10px;
}

.result-output-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.result-output-title {
  color: #262626;
  font-size: 13px;
  font-weight: 700;
}

.json-pre {
  min-height: 160px;
  margin: 0 0 10px;
  padding: 8px 12px;
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #F2F4F7;
  color: #262626;
  font-size: 12px;
  line-height: 1.6;
}

.json-pre.compact {
  min-height: auto;
  max-height: 220px;
  margin-bottom: 0;
  background: #fcfcfc;
}

.execution-item {
  border-radius: 8px;
  background: #F2F4F7;
  padding-bottom: 10px;
  overflow: hidden;
}

.execution-head {
  padding: 10px;
  cursor: pointer;
}

.execution-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.execution-title {
  color: #262626;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.execution-error {
  margin: 0 10px 8px;
}

@media (max-width: 900px) {
  .run-dock {
    left: 12px;
    right: 12px;
    max-width: none;
    width: auto !important;
  }
}

:deep(.ant-collapse-header) {
  padding: 4px 10px !important;
}
:deep(.ant-collapse-content-box) {
  padding: 4px 10px 4px 32px !important;
}
</style>

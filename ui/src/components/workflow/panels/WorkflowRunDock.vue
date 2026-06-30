<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CloseOutlined, PlayCircleOutlined, BugOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import SmartCodeEditor from '@/components/editor/SmartCodeEditor.vue'
import type { WorkflowFlowNode, WorkflowNodeExecution, WorkflowRunRequest, WorkflowRunResult } from '@/types/workflow'

const props = defineProps<{
  open: boolean
  result: WorkflowRunResult | null
  nodes: WorkflowFlowNode[]
  loading?: boolean
}>()

const inputText = defineModel<string>('inputText', { default: '{\n  "params": [],\n  "variables": {}\n}' })
const width = defineModel<number>('width', { default: 440 })

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

function resetFromStartParams() {
  const next: Record<string, unknown> = {}
  startParams.value.forEach((param) => {
    const name = String(param.name || '')
    if (!name) return
    next[name] = paramValues.value[name] ?? param.value ?? defaultValueByType(String(param.type || 'String'))
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
    issues.push('变量不是合法 JSON')
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

function statusColor(status?: string) {
  if (status === 'SUCCESS') return 'green'
  if (status === 'FAIL') return 'red'
  if (status === 'RUNNING') return 'blue'
  return 'default'
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
    width.value = Math.max(440, Math.min(maxWidth.value, next))
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
        <AButton type="primary" :loading="loading" @click="runWithValidation">
          <template #icon><PlayCircleOutlined /></template>
          运行草稿
        </AButton>
        <AButton type="text" @click="emit('close')">
          <template #icon><CloseOutlined /></template>
        </AButton>
      </div>
    </header>

    <ATabs v-model:active-key="activeKey" size="small" class="dock-tabs">
      <ATabPane key="input" tab="调试输入">
        <div class="debug-input">
          <div class="debug-section-head">
            <div>
              <div class="debug-section-title">开始节点参数</div>
              <div class="debug-section-desc">根据开始节点的输入配置自动生成。</div>
            </div>
            <AButton size="small" @click="resetFromStartParams">
              <template #icon><ReloadOutlined /></template>
              重置
            </AButton>
          </div>

          <AAlert
            v-if="inputIssues.length"
            type="warning"
            show-icon
            :message="inputIssues[0]"
          />

          <div v-if="hasStartParams" class="param-list">
            <div v-for="param in startParams" :key="String(param.name)" class="param-row">
              <div class="param-label">
                <span class="param-name">{{ param.name }}</span>
                <ATag v-if="param.required" color="red" :bordered="false">必填</ATag>
                <ATag color="blue" :bordered="false">{{ param.type || 'String' }}</ATag>
              </div>
              <ASwitch
                v-if="param.type === 'Boolean'"
                v-model:checked="paramValues[String(param.name)]"
                size="small"
              />
              <ATextarea
                v-else-if="param.type === 'Object' || param.type === 'Array'"
                v-model:value="paramValues[String(param.name)]"
                :auto-size="{ minRows: 2, maxRows: 5 }"
                placeholder="请输入 JSON"
              />
              <AInput
                v-else
                v-model:value="paramValues[String(param.name)]"
                :placeholder="String(param.remark || '请输入调试值')"
              />
              <div v-if="param.remark" class="param-remark">{{ param.remark }}</div>
            </div>
          </div>
          <AEmpty v-else description="开始节点未定义输入参数" />

          <div class="debug-section-title mt">变量</div>
          <SmartCodeEditor
            v-model="variablesText"
            language="json"
            theme="light"
            height="120px"
            :show-change-language="false"
            :show-theme-toggle="false"
            :show-fullscreen="false"
          />

          <ACollapse ghost>
            <ACollapsePanel key="json" header="高级 JSON">
              <SmartCodeEditor
                v-model="inputText"
                language="json"
                theme="light"
                height="180px"
                :show-change-language="false"
                :show-theme-toggle="false"
                :show-fullscreen="false"
              />
            </ACollapsePanel>
          </ACollapse>
        </div>
      </ATabPane>

      <ATabPane key="result" tab="运行结果">
        <div class="result-summary">
          <div class="result-card">
            <span class="result-label">状态</span>
            <ATag :color="statusColor(runStatus)" :bordered="false">{{ runStatus || '-' }}</ATag>
          </div>
          <div class="result-card">
            <span class="result-label">耗时</span>
            <span>{{ duration(result?.run?.startTime, result?.run?.endTime) }}</span>
          </div>
        </div>
        <pre class="json-pre">{{ formatJson(finalOutput ?? result?.run) }}</pre>
        <AAlert v-if="result?.run?.error" type="error" show-icon :message="result.run.error" />
      </ATabPane>

      <ATabPane key="nodes" tab="节点日志">
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
              <ATag :color="statusColor(item.status)">
                {{ item.status }}
              </ATag>
            </div>
            <AAlert v-if="item.error" class="execution-error" type="error" show-icon :message="item.error" />
            <ACollapse ghost size="small">
              <ACollapsePanel key="inputs" header="输入">
                <pre class="json-pre compact">{{ formatJson(item.inputs) }}</pre>
              </ACollapsePanel>
              <ACollapsePanel key="outputs" header="输出">
                <pre class="json-pre compact">{{ formatJson(item.outputs) }}</pre>
              </ACollapsePanel>
              <ACollapsePanel key="process" header="处理数据">
                <pre class="json-pre compact">{{ formatJson(item.processData) }}</pre>
              </ACollapsePanel>
            </ACollapse>
          </div>
        </div>
        <AEmpty v-else description="暂无节点日志" />
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
  min-width: 440px;
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
.param-label {
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
  padding: 0 14px 14px;
  overflow: auto;
}

.debug-input,
.param-list,
.execution-list {
  display: grid;
  gap: 10px;
}

.debug-section-title {
  color: #262626;
  font-size: 13px;
  font-weight: 700;
}

.debug-section-title.mt {
  margin-top: 4px;
}

.param-row {
  display: grid;
  gap: 6px;
  padding: 10px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.param-name {
  color: #262626;
  font-weight: 600;
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
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.result-label {
  color: #8c8c8c;
  font-size: 12px;
}

.json-pre {
  min-height: 160px;
  margin: 0 0 10px;
  padding: 12px;
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
  color: #262626;
  font-size: 12px;
  line-height: 1.6;
}

.json-pre.compact {
  min-height: auto;
  max-height: 220px;
  margin-bottom: 0;
}

.execution-item {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
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
</style>

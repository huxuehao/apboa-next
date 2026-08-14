<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  CheckCircleFilled,
  CloseCircleFilled,
  CloseOutlined,
  ExclamationCircleFilled,
  PlayCircleFilled,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import IconFont from '@/components/common/IconFont.vue'
import { getNodeIconName } from '@/config/workflow/common'
import type {
  WorkflowFlowNode,
  WorkflowNodeRunRequest,
  WorkflowNodeRunResult,
  WorkflowInputConfig,
} from '@/types/workflow'

interface NodeRunInputItem {
  name: string
  type: string
  value: unknown
}

const props = defineProps<{
  open: boolean
  node: WorkflowFlowNode | null
  loading: boolean
  result: WorkflowNodeRunResult | null
  rightOffset: number
}>()

const width = defineModel<number>('width', { default: 442 })

const emit = defineEmits<{
  run: [payload: WorkflowNodeRunRequest]
  close: []
}>()

const TYPE_OPTIONS = [
  { label: 'string', value: 'String' },
  { label: 'long', value: 'Long' },
  { label: 'integer', value: 'Integer' },
  { label: 'float', value: 'Float' },
  { label: 'double', value: 'Double' },
  { label: 'boolean', value: 'Boolean' },
  { label: 'array', value: 'Array' },
  { label: 'object', value: 'Object' },
]

const activeKey = ref<'input' | 'output'>('input')
const inputItems = ref<NodeRunInputItem[]>([])
const variablesText = ref('{}')
const inputIssues = ref<string[]>([])
const dragging = ref(false)
const maxWidth = computed(() => Math.floor(window.innerWidth * 0.55))

const nodeInputConfigs = computed<WorkflowInputConfig[]>(() =>
  Array.isArray(props.node?.data.inputConfigs) ? (props.node!.data.inputConfigs as WorkflowInputConfig[]) : [],
)

const nodeOutputInputs = computed(() =>
  nodeInputConfigs.value.filter((config) => config.sourceType === 'NODE_OUTPUT'),
)

const hasVariableInputs = computed(() =>
  nodeInputConfigs.value.some(
    (config) => config.sourceType === 'VARIABLE' || config.sourceType === 'EXPRESSION',
  ),
)

const runStatus = computed(() => props.result?.status)

const nodeColor = computed(() => props.node?.data.schema?.color || '#1677ff')
const nodeIconName = computed(() => getNodeIconName(props.node?.data.type || ''))

watch(
  [() => props.open, () => props.node?.id],
  () => {
    if (props.open && props.node) resetInputs()
    activeKey.value = 'input'
  },
  { immediate: true },
)

watch(
  () => props.result,
  (result) => {
    if (result) activeKey.value = 'output'
  },
)

function defaultValueByType(type: string): unknown {
  if (type === 'Boolean') return false
  if (type === 'Array') return '[]'
  if (type === 'Object') return '{}'
  return ''
}

function resetInputs() {
  inputItems.value = nodeOutputInputs.value.map((config) => ({
    name: config.name,
    type: config.type || 'String',
    value: defaultValueByType(config.type || 'String'),
  }))
  variablesText.value = '{}'
}

function normalizeValue(value: unknown, type: string): unknown {
  if (type === 'Boolean') return Boolean(value)
  return value == null ? '' : value
}

function buildPayload(): WorkflowNodeRunRequest {
  const inputs: Record<string, unknown> = {}
  const inputTypes: Record<string, string> = {}
  inputItems.value.forEach((item) => {
    inputs[item.name] = normalizeValue(item.value, item.type)
    inputTypes[item.name] = item.type
  })

  let variables: Record<string, unknown> = {}
  try {
    variables = JSON.parse(variablesText.value || '{}')
  } catch {
    variables = {}
  }

  return {
    node: {
      id: props.node!.id,
      type: props.node!.data.type,
      name: props.node!.data.label,
      config: props.node!.data.config || {},
      inputConfigs: nodeInputConfigs.value,
      outputConfigs: props.node!.data.outputConfigs || [],
    },
    inputs,
    inputTypes,
    variables,
  }
}

function collectInputIssues() {
  const issues: string[] = []
  inputItems.value.forEach((item) => {
    if (item.type === 'Object' || item.type === 'Array') {
      try {
        const parsed = JSON.parse(String(item.value ?? ''))
        if (item.type === 'Array' && !Array.isArray(parsed)) {
          issues.push(`${item.name} 必须是 JSON 数组`)
        }
        if (
          item.type === 'Object' &&
          (Array.isArray(parsed) || typeof parsed !== 'object' || parsed === null)
        ) {
          issues.push(`${item.name} 必须是 JSON 对象`)
        }
      } catch {
        issues.push(`${item.name} 不是合法 JSON`)
      }
    }
    if (['Long', 'Integer', 'Float', 'Double'].includes(item.type) && !Number.isFinite(Number(item.value))) {
      issues.push(`${item.name} 必须是数字`)
    }
  })
  try {
    JSON.parse(variablesText.value || '{}')
  } catch {
    issues.push('全局变量不是合法 JSON')
  }
  return issues
}

function runWithValidation() {
  if (!props.node) {
    message.warning('节点不存在，请重新选择要运行的节点')
    return
  }
  inputIssues.value = collectInputIssues()
  if (inputIssues.value.length) {
    activeKey.value = 'input'
    message.warning(inputIssues.value[0])
    return
  }
  emit('run', buildPayload())
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

function statusText(status?: string) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAIL') return '失败'
  if (status === 'RUNNING') return '运行中'
  return '-'
}

function durationText() {
  const duration = props.result?.duration
  if (typeof duration === 'number') return `${duration} ms`
  const start = props.result?.startTime
  const end = props.result?.endTime
  if (start && end) return `${Math.max(0, end - start)} ms`
  return '-'
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
  <section
    v-if="open"
    class="node-run-panel"
    :class="{ dragging }"
    :style="{ width: `${width}px`, right: `${rightOffset}px` }"
  >
    <div class="resize-handle" @mousedown.prevent="beginResize" />

    <header class="panel-header">
      <div class="panel-heading">
        <span class="node-avatar" :style="{ backgroundColor: `${nodeColor}CC` }">
          <IconFont :name="nodeIconName" :size="24" color="#ffffff" />
        </span>
        <div class="heading-copy">
          <div class="panel-title" :title="'节点运行'">节点运行</div>
          <div class="panel-subtitle" :title="node?.data.label || '未选择节点'">{{ node?.data.label || '未选择节点' }}</div>
        </div>
      </div>
      <AButton type="text" @click="emit('close')">
        <template #icon><CloseOutlined /></template>
      </AButton>
    </header>

    <ATabs v-model:active-key="activeKey" size="small" class="panel-tabs">
      <ATabPane key="input" tab="输入">
        <div class="input-wrap">
          <div class="input-scroll">
            <AAlert
              v-if="inputIssues.length"
              type="warning"
              style="margin-bottom: 8px"
              show-icon
              banner
              closable
              :message="inputIssues[0]"
            />

            <div class="section-head">
              <div>
                <div class="section-title">节点输入</div>
                <div class="section-desc">原「节点输出」输入将改为固定值，请输入测试数据。</div>
              </div>
              <AButton v-if="nodeOutputInputs.length" type="text" size="small" @click="resetInputs">
                <template #icon><ReloadOutlined /></template>
                重置
              </AButton>
            </div>

            <div v-if="nodeOutputInputs.length" class="input-list">
              <div v-for="item in inputItems" :key="item.name" class="input-card">
                <div class="input-head">
                  <span class="input-name">{{ item.name }}</span>
                  <ASelect
                    v-model:value="item.type"
                    :options="TYPE_OPTIONS"
                    size="small"
                    style="width: 96px"
                  />
                </div>

                <ASwitch
                  v-if="item.type === 'Boolean'"
                  v-model:checked="item.value"
                  style="width: 30px"
                />
                <AInput
                  v-else-if="item.type !== 'Object' && item.type !== 'Array'"
                  v-model:value="item.value"
                  placeholder="请输入测试值"
                />
                <ATextarea
                  v-else
                  v-model:value="item.value"
                  :auto-size="{ minRows: 2, maxRows: 5 }"
                  placeholder="请输入 JSON"
                />
              </div>
            </div>
            <AEmpty v-else description="该节点没有需要测试输入的「节点输出」输入" />

            <template v-if="hasVariableInputs">
              <div class="section-title mt">全局变量</div>
              <div class="section-desc">为「全局变量」和「表达式」输入提供变量值，格式为 JSON 对象。</div>
              <ConfigCodeEditor
                v-model:model-value="variablesText"
                language="json"
                height="120px"
                :maximize="false"
              />
            </template>
          </div>

          <div class="input-footer">
            <AButton type="primary" :loading="loading" block @click="runWithValidation">
              <template #icon><PlayCircleOutlined /></template>
              运行
            </AButton>
          </div>
        </div>
      </ATabPane>

      <ATabPane key="output" tab="输出">
        <div class="output-scroll">
          <template v-if="result">
            <div class="summary">
              <div class="summary-card">
                <span class="summary-label">状态</span>
                <ATooltip :title="statusText(runStatus)">
                  <CheckCircleFilled v-if="runStatus === 'SUCCESS'" style="color: #52c41a; font-size: 16px" />
                  <CloseCircleFilled v-else-if="runStatus === 'FAIL'" style="color: #ff4d4f; font-size: 16px" />
                  <PlayCircleFilled v-else-if="runStatus === 'RUNNING'" style="color: #1677ff; font-size: 16px" />
                  <ExclamationCircleFilled v-else style="color: #8c8c8c; font-size: 16px" />
                </ATooltip>
              </div>
              <div class="summary-card">
                <span class="summary-label">耗时</span>
                <span>{{ durationText() }}</span>
              </div>
            </div>

            <div v-if="result.error" class="output-head">
              <span class="output-title error">错误信息</span>
            </div>
            <pre v-if="result.error" class="json-pre">{{ result.error }}</pre>

            <div class="output-head">
              <span class="output-title">输出结果</span>
            </div>
            <pre class="json-pre">{{ formatJson(result.output ?? result.outputs) }}</pre>

            <div class="output-head">
              <span class="output-title">运行日志</span>
            </div>
            <pre class="json-pre">{{ formatJson(result.executionContext) }}</pre>
          </template>
          <AEmpty v-else description="暂无运行结果，点击「运行」按钮开始执行" />
        </div>
      </ATabPane>
    </ATabs>
  </section>
</template>

<style scoped lang="scss">
.node-run-panel {
  position: absolute;
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
  background: #1677ff;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
}

.panel-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.node-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 35px;
  height: 35px;
  border-radius: 10px;
  flex-shrink: 0;
}

.heading-copy {
  width: 0;
  flex: 1;
  overflow: hidden;
}

.panel-title {
  display: block;
  width: 100%;
  margin-bottom: 2px;
  color: #262626;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-subtitle,
.section-desc {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.4;
}

.panel-subtitle {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-tabs {
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

.input-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.input-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.input-footer {
  flex-shrink: 0;
  padding-bottom: 16px;
}

.output-scroll {
  height: calc(100% - 16px);
  overflow: auto;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.section-title {
  color: #262626;
  font-size: 13px;
  font-weight: 700;
}

.section-title.mt {
  margin-top: 12px;
}

.input-list {
  display: grid;
  gap: 10px;
  margin-bottom: 4px;
}

.input-card {
  display: grid;
  gap: 8px;
  padding: 8px 12px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.input-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.input-name {
  flex: 1;
  min-width: 0;
  color: #262626;
  font-weight: 600;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.summary-card {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  background: #f2f4f7;
}

.summary-label {
  color: #8c8c8c;
  font-size: 12px;
}

.output-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 10px 0 6px;
}

.output-title {
  color: #262626;
  font-size: 13px;
  font-weight: 700;
}

.output-title.error {
  color: #cf1322;
}

.json-pre {
  min-height: 40px;
  margin: 0 0 10px;
  padding: 8px 12px;
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #f2f4f7;
  color: #262626;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .node-run-panel {
    left: 12px;
    right: 12px;
    max-width: none;
    width: auto !important;
  }
}
</style>

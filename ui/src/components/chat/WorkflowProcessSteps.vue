<script setup lang="ts">
/**
 * 工作流实际执行过程：运行中消费节点增量，完成后消费 nodeExecutions 权威快照。
 * 组件只展示已进入执行的节点，不根据画布拓扑推测未执行节点。
 */
import { computed, ref } from 'vue'
import {
  CheckCircleFilled,
  CheckOutlined,
  CloseCircleFilled,
  CopyOutlined,
  DownOutlined,
  LoadingOutlined,
  MinusCircleFilled,
  RightOutlined,
} from '@ant-design/icons-vue'
import type { WorkflowProcess, WorkflowProcessNode } from '@/types'
import { formatElapsed } from '@/utils/chat/format'

const props = defineProps<{
  process: WorkflowProcess
}>()

const NODE_TYPE_LABELS: Record<string, string> = {
  START: '开始',
  END: '结束',
  AGENT: '智能体',
  LLM: '语言模型',
  TOOL_EXECUTE: '工具',
  MCP_CALL: 'MCP',
  HTTP_EXTERNAL: '外部 HTTP',
  HTTP_INLINE: '内联 HTTP',
  IF_ELSE: '条件',
  CLASSIFY: '分类',
  LOOP: '循环',
  ITERATE: '迭代',
  CODE: '代码',
  DB_SELECT: '数据库查询',
  DB_INSERT: '数据库新增',
  DB_UPDATE: '数据库更新',
  DB_DELETE: '数据库删除',
  CACHE_FETCH: '读取缓存',
  CACHE_SET: '写入缓存',
  CACHE_REFRESH: '刷新缓存',
  CACHE_REMOVE: '删除缓存',
  MQ_PUSH: '消息队列',
  PLUGIN: '插件',
  SERIALIZE: '序列化',
  UNSERIALIZE: '反序列化',
  STRING_TEMPLATE: '字符串模板',
  STRING_SPLIT: '字符串分割',
  LIST_FILTER: '列表过滤',
  LIST_SORT: '列表排序',
  VARIABLE_AGG: '变量聚合',
  NON_EMPTY_SELECT: '非空选择',
  MATCH_RESULT: '结果匹配',
}

const expanded = ref<Record<number, boolean>>({})
const copiedKey = ref<string | null>(null)

const nodes = computed(() => Array.isArray(props.process.nodes) ? props.process.nodes : [])
const processStatusText = computed(() => statusText(props.process.status))
const processFailed = computed(() => props.process.status === 'FAIL')
const processRunning = computed(() => props.process.status === 'RUNNING')

function toggle(index: number) {
  expanded.value[index] = !expanded.value[index]
}

function nodeTitle(node: WorkflowProcessNode, index: number): string {
  return node.title || NODE_TYPE_LABELS[node.type || ''] || `节点 ${index + 1}`
}

function nodeType(node: WorkflowProcessNode): string {
  return NODE_TYPE_LABELS[node.type || ''] || node.type || '节点'
}

function statusText(status?: string): string {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAIL') return '失败'
  if (status === 'RUNNING') return '运行中'
  if (status === 'REQUESTED') return '待执行'
  if (status === 'SUSPENDED') return '已挂起'
  if (status === 'STOP') return '已停止'
  return status || '未知'
}

function hasContent(value?: string): boolean {
  return !!value && value !== '{}'
}

function prettyJson(value?: string): string {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function providerDuration(request: { providerMetrics?: Record<string, string | number | boolean> }, key: string): string {
  const raw = request.providerMetrics?.[key]
  const nanoseconds = typeof raw === 'number' ? raw : Number(raw)
  return Number.isFinite(nanoseconds) ? formatElapsed(nanoseconds / 1_000_000) : String(raw || '')
}

async function copyContent(key: string, value?: string) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    copiedKey.value = key
    setTimeout(() => {
      if (copiedKey.value === key) copiedKey.value = null
    }, 2000)
  } catch {
    // 剪贴板不可用时静默降级。
  }
}
</script>

<template>
  <section class="workflow-process">
    <div class="workflow-process-title">
      <span>工作流过程</span>
      <span
        class="workflow-process-summary"
        :class="processFailed ? 'is-fail' : processRunning ? 'is-running' : 'is-success'"
      >
        {{ processStatusText }} · {{ nodes.length }} 个节点
        <template v-if="process.elapsed != null"> · {{ formatElapsed(process.elapsed) }}</template>
      </span>
    </div>

    <div v-if="hasContent(process.error)" class="workflow-process-run-error">
      {{ process.error }}
    </div>

    <div v-for="(node, index) in nodes" :key="node.invocationId || `${node.nodeId}-${index}`" class="workflow-process-node">
      <div class="workflow-process-node-head" @click="toggle(index)">
        <span class="workflow-process-arrow">
          <DownOutlined v-if="expanded[index]" />
          <RightOutlined v-else />
        </span>
        <CheckCircleFilled v-if="node.status === 'SUCCESS'" class="workflow-status-icon is-success" />
        <CloseCircleFilled v-else-if="node.status === 'FAIL'" class="workflow-status-icon is-fail" />
        <LoadingOutlined v-else-if="node.status === 'RUNNING'" spin class="workflow-status-icon is-running" />
        <MinusCircleFilled v-else class="workflow-status-icon is-stop" />
        <span class="workflow-process-node-index">{{ index + 1 }}.</span>
        <span class="workflow-process-node-title">{{ nodeTitle(node, index) }}</span>
        <span class="workflow-process-node-type">{{ nodeType(node) }}</span>
        <span class="workflow-process-node-status" :class="`is-${(node.status || 'stop').toLowerCase()}`">
          {{ statusText(node.status) }}<template v-if="node.elapsed != null"> · {{ formatElapsed(node.elapsed) }}</template>
        </span>
      </div>

      <div v-show="expanded[index]" class="workflow-process-node-body">
        <div v-if="node.modelName" class="workflow-process-model">
          模型：{{ node.modelName }}
        </div>

        <div v-if="node.modelRequests?.length" class="workflow-model-requests">
          <div class="workflow-process-section-label">模型请求记录</div>
          <div
            v-for="request in node.modelRequests"
            :key="request.requestIndex"
            class="workflow-model-request"
          >
            <div v-if="node.modelRequests.length > 1" class="workflow-model-request-title">
              第 {{ request.requestIndex }} 次模型调用
            </div>
            <div class="workflow-model-request-metrics">
              <span v-if="request.inputTokens != null">输入 {{ request.inputTokens }} tokens</span>
              <span v-if="request.outputTokens != null">输出 {{ request.outputTokens }} tokens</span>
              <span v-if="request.durationMs != null">总耗时 {{ formatElapsed(request.durationMs) }}</span>
              <span v-if="request.ttftMs != null">首 token {{ formatElapsed(request.ttftMs) }}</span>
              <span v-if="request.finishReason">finish={{ request.finishReason }}</span>
              <span v-if="request.generateReason">reason={{ request.generateReason }}</span>
              <span v-if="request.thinkingChars">隐藏思考 {{ request.thinkingChars }} 字符</span>
            </div>
            <div v-if="request.providerMetrics && Object.keys(request.providerMetrics).length" class="workflow-model-provider-metrics">
              <span v-if="request.providerMetrics.load_duration != null">
                加载 {{ providerDuration(request, 'load_duration') }}
              </span>
              <span v-if="request.providerMetrics.total_duration != null">
                Provider 总耗时 {{ providerDuration(request, 'total_duration') }}
              </span>
              <span v-if="request.providerMetrics.prompt_eval_duration != null">
                Prompt Eval {{ providerDuration(request, 'prompt_eval_duration') }}
              </span>
              <span v-if="request.providerMetrics.eval_duration != null">
                Eval {{ providerDuration(request, 'eval_duration') }}
              </span>
              <span v-if="request.providerMetrics.prompt_eval_count != null">
                Prompt {{ request.providerMetrics.prompt_eval_count }} tokens
              </span>
              <span v-if="request.providerMetrics.eval_count != null">
                生成 {{ request.providerMetrics.eval_count }} tokens
              </span>
            </div>
            <div
              v-for="attempt in request.attempts"
              :key="attempt.attempt"
              class="workflow-model-attempt"
            >
              <CheckCircleFilled v-if="attempt.status === 'SUCCESS'" class="workflow-status-icon is-success" />
              <CloseCircleFilled v-else-if="attempt.status === 'FAIL'" class="workflow-status-icon is-fail" />
              <MinusCircleFilled v-else class="workflow-status-icon is-stop" />
              <!-- 单次成功是常态，重试上限对读者无信息量：仅发生多次尝试时才显示序号区分各行 -->
              <span v-if="request.attempts.length > 1">尝试 {{ attempt.attempt }}/{{ request.maxAttempts }}</span>
              <span class="workflow-model-attempt-status" :class="attempt.status === 'SUCCESS' ? 'is-success' : 'is-fail'">
                {{ statusText(attempt.status) }}<template v-if="attempt.elapsed != null"> · {{ formatElapsed(attempt.elapsed) }}</template>
                <template v-if="attempt.ttft != null"> · 首响 {{ formatElapsed(attempt.ttft) }}</template>
              </span>
              <span v-if="attempt.detail" class="workflow-model-attempt-detail">{{ attempt.detail }}</span>
            </div>
            <div v-if="request.toolCalls?.length" class="workflow-model-tool-calls">
              <div class="workflow-model-tool-title">实际工具调用</div>
              <div v-for="(tool, toolIndex) in request.toolCalls" :key="tool.id || `${request.requestIndex}-${tool.name}-${toolIndex}`" class="workflow-model-tool-call">
                <span class="workflow-model-tool-name">{{ tool.name || '未知工具' }}</span>
                <span class="workflow-model-tool-status" :class="tool.status === 'SUCCESS' ? 'is-success' : tool.status === 'FAIL' ? 'is-fail' : 'is-stop'">
                  {{ statusText(tool.status) }}<template v-if="tool.elapsed != null"> · {{ formatElapsed(tool.elapsed) }}</template>
                </span>
                <code v-if="tool.arguments">{{ tool.arguments }}</code>
                <span v-if="tool.detail" class="workflow-model-attempt-detail">{{ tool.detail }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="hasContent(node.error)" class="workflow-process-data is-error">
          <div class="workflow-process-data-head">
            <span>错误</span>
            <span class="workflow-process-copy" @click.stop="copyContent(`${index}-error`, node.error)">
              <CheckOutlined v-if="copiedKey === `${index}-error`" />
              <CopyOutlined v-else />
            </span>
          </div>
          <pre>{{ prettyJson(node.error) }}</pre>
        </div>

        <div v-if="hasContent(node.inputs)" class="workflow-process-data">
          <div class="workflow-process-data-head">
            <span>输入</span>
            <span class="workflow-process-copy" @click.stop="copyContent(`${index}-inputs`, node.inputs)">
              <CheckOutlined v-if="copiedKey === `${index}-inputs`" />
              <CopyOutlined v-else />
            </span>
          </div>
          <pre>{{ prettyJson(node.inputs) }}</pre>
        </div>

        <div v-if="hasContent(node.outputs)" class="workflow-process-data">
          <div class="workflow-process-data-head">
            <span>输出</span>
            <span class="workflow-process-copy" @click.stop="copyContent(`${index}-outputs`, node.outputs)">
              <CheckOutlined v-if="copiedKey === `${index}-outputs`" />
              <CopyOutlined v-else />
            </span>
          </div>
          <pre>{{ prettyJson(node.outputs) }}</pre>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.workflow-process {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.workflow-process-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: rgba(0, 0, 0, 0.68);
  font-size: 12px;
  font-weight: 600;
}

.workflow-process-summary {
  font-size: 11px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.is-success { color: #389e0d; }
.is-fail { color: #cf1322; }
.is-stop { color: rgba(0, 0, 0, 0.4); }
.is-running { color: #1677ff; }

.workflow-process-run-error {
  padding: 5px 8px;
  border-radius: 5px;
  background: #fff2f0;
  color: #cf1322;
  font-size: 11px;
  white-space: pre-wrap;
}

.workflow-process-node {
  border-left: 1px solid #e8e8e8;
  margin-left: 5px;
  padding-left: 9px;
}

.workflow-process-node-head {
  display: flex;
  align-items: center;
  min-height: 26px;
  gap: 5px;
  cursor: pointer;
  font-size: 12px;
}

.workflow-process-arrow {
  width: 12px;
  color: rgba(0, 0, 0, 0.4);
  font-size: 9px;
}

.workflow-status-icon { flex: 0 0 auto; font-size: 12px; }
.workflow-process-node-index { color: rgba(0, 0, 0, 0.45); }
.workflow-process-node-title { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.workflow-process-node-type {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f5f5f5;
  color: rgba(0, 0, 0, 0.5);
  font-size: 10px;
}
.workflow-process-node-status {
  flex: 0 0 auto;
  margin-left: auto;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.workflow-process-node-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 2px 0 8px 29px;
}

.workflow-process-model,
.workflow-process-section-label,
.workflow-model-request-title {
  color: rgba(0, 0, 0, 0.6);
  font-size: 11px;
}
.workflow-process-section-label { font-weight: 600; }
.workflow-model-request-title { margin: 2px 0; }

.workflow-model-request {
  padding: 4px 0;
}
.workflow-model-request + .workflow-model-request {
  border-top: 1px dashed #e8e8e8;
}
.workflow-model-request-metrics,
.workflow-model-provider-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin: 2px 0 4px 19px;
  color: rgba(0, 0, 0, 0.52);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}
.workflow-model-provider-metrics {
  color: #1677ff;
}

.workflow-model-attempt {
  display: grid;
  grid-template-columns: 14px auto auto minmax(0, 1fr);
  align-items: center;
  gap: 5px;
  min-height: 22px;
  font-size: 11px;
}
.workflow-model-attempt-status { font-variant-numeric: tabular-nums; white-space: nowrap; }
.workflow-model-attempt-detail {
  min-width: 0;
  overflow: hidden;
  color: rgba(0, 0, 0, 0.45);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-model-tool-calls {
  margin: 4px 0 0 19px;
  font-size: 10px;
}
.workflow-model-tool-title {
  margin-bottom: 2px;
  color: rgba(0, 0, 0, 0.55);
  font-weight: 600;
}
.workflow-model-tool-call {
  display: grid;
  grid-template-columns: minmax(100px, auto) auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  padding: 2px 0;
}
.workflow-model-tool-name { font-weight: 500; }
.workflow-model-tool-status { white-space: nowrap; }
.workflow-model-tool-call code {
  min-width: 0;
  overflow: hidden;
  color: rgba(0, 0, 0, 0.52);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-process-data {
  min-width: 0;
}
.workflow-process-data-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: rgba(0, 0, 0, 0.55);
  font-size: 11px;
}
.workflow-process-data.is-error .workflow-process-data-head { color: #cf1322; }
.workflow-process-copy { cursor: pointer; font-size: 11px; }
.workflow-process-data pre {
  max-height: 220px;
  margin: 3px 0 0;
  padding: 7px 9px;
  overflow: auto;
  border-radius: 5px;
  background: #f7f8fa;
  color: rgba(0, 0, 0, 0.72);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 768px) {
  .workflow-process-node-body { padding-left: 12px; }
  .workflow-model-attempt { grid-template-columns: 14px 1fr auto; }
  .workflow-model-attempt-detail { grid-column: 2 / -1; }
}
</style>

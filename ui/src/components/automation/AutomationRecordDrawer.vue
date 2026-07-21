/**
 * 运行记录抽屉
 * 展示自动化任务每次运行的业务记录（来源于 JobRecord 表），按创建时间倒序
 * 点击工作流任务的记录可查看节点执行详情，点击智能体任务的记录可查看对话详情
 *
 * @component
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import {
  CheckCircleFilled,
  CloseCircleFilled,
  PlayCircleFilled,
  ExclamationCircleFilled,
  ArrowLeftOutlined,
} from '@ant-design/icons-vue'
import { Collapse as ACollapse, CollapsePanel as ACollapsePanel } from 'ant-design-vue'
import type { JobInfo } from '@/types'
import * as automationApi from '@/api/automation'
import { workflowRunNodes } from '@/api/workflow'
import * as chatSessionApi from '@/api/chatSession'
import MessageList from '@/components/chatHistory/MessageList.vue'
import type { JobRecordVO } from '@/types/automation'
import type { WorkflowNodeExecution } from '@/types/workflow'
import type { ChatMessageVO, DisplayMessage } from '@/types'

const visible = defineModel<boolean>('visible', { default: false })
const job = defineModel<JobInfo | null>('job', { default: null })

const loading = ref(false)
const records = ref<JobRecordVO[]>([])
const selectedRecord = ref<JobRecordVO | null>(null)

const nodesLoading = ref(false)
const nodeExecutions = ref<WorkflowNodeExecution[]>([])

const agentMessagesLoading = ref(false)
const agentMessages = ref<ChatMessageVO[]>([])

const viewMode = ref<'list' | 'workflow-detail' | 'agent-detail'>('list')

async function loadRecords() {
  if (!job.value?.id) return
  loading.value = true
  try {
    const res = await automationApi.getRecords(String(job.value.id))
    records.value = res.data.data || []
  } catch (e) {
    console.error('加载运行记录失败:', e)
  } finally {
    loading.value = false
  }
}

/**
 * 加载工作流节点执行详情
 */
async function loadNodeExecutions(recordId: number) {
  nodesLoading.value = true
  try {
    const res = await workflowRunNodes(String(recordId))
    nodeExecutions.value = res.data.data || []
  } catch (e) {
    console.error('加载节点执行记录失败:', e)
    nodeExecutions.value = []
  } finally {
    nodesLoading.value = false
  }
}

/**
 * 加载智能体会话消息
 */
async function loadAgentMessages(recordId: number) {
  agentMessagesLoading.value = true
  try {
    const res = await chatSessionApi.getCurrentMessages(String(recordId))
    agentMessages.value = res.data.data || []
  } catch (e) {
    console.error('加载对话消息失败:', e)
    agentMessages.value = []
  } finally {
    agentMessagesLoading.value = false
  }
}

/**
 * 格式化创建时间
 */
function formatTime(time: string) {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function handleSelect(record: JobRecordVO) {
  selectedRecord.value = record
  if (!record.recordId) return
  if (job.value?.type === 'WORKFLOW') {
    viewMode.value = 'workflow-detail'
    loadNodeExecutions(record.recordId)
  } else if (job.value?.type === 'AGENT') {
    viewMode.value = 'agent-detail'
    loadAgentMessages(record.recordId)
  }
}

function handleBack() {
  viewMode.value = 'list'
  nodeExecutions.value = []
  agentMessages.value = []
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

function nodeDuration(start?: number, end?: number) {
  if (!start || !end) return '-'
  return `${Math.max(0, end - start)} ms`
}

function statusText(status?: string) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAIL') return '失败'
  if (status === 'RUNNING') return '运行中'
  return '-'
}

function executionTitle(item: WorkflowNodeExecution, index: number) {
  return `${index + 1}. ${item.nodeTitle || item.nodeId}`
}

/**
 * 从消息内容中解析推理和正文
 */
function parseMessageContent(raw: string): { content: string; reasoningContent?: string } {
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && 'reasoning' in parsed && 'content' in parsed) {
      return { content: parsed.content as string, reasoningContent: parsed.reasoning as string }
    }
  } catch {
    // not JSON, return as-is
  }
  return { content: raw }
}

/**
 * 过滤 system 根消息，解析 assistant 推理内容
 */
const visibleAgentMessages = computed<DisplayMessage[]>(() => {
  const list: DisplayMessage[] = []
  const filtered = agentMessages.value.filter(m => !(m.role === 'system' && m.depth === 0))

  for (const m of filtered) {
    const parsed = m.role === 'assistant'
      ? parseMessageContent(m.content || '')
      : { content: m.content || '', reasoningContent: undefined }

    list.push({
      id: String(m.id),
      role: m.role as DisplayMessage['role'],
      content: parsed.content,
      createdAt: m.createdAt,
      isStreaming: false,
    })
  }

  return list
})

watch(visible, (val) => {
  if (val) {
    selectedRecord.value = null
    viewMode.value = 'list'
    nodeExecutions.value = []
    agentMessages.value = []
    loadRecords()
  }
})
</script>

<template>
  <ADrawer
    v-model:open="visible"
    :title="viewMode === 'list' ? '运行记录' : viewMode === 'workflow-detail' ? '节点执行详情' : '对话详情'"
    :width="viewMode === 'workflow-detail' ? 560 : viewMode === 'agent-detail' ? 720 : 480"
  >
    <!-- 记录列表视图 -->
    <template v-if="viewMode === 'list'">
      <ApboaSpin :spinning="loading">
        <div v-if="records.length === 0 && !loading" class="empty-state">
          <AEmpty description="暂无运行记录" />
        </div>

        <div v-else class="records-container">
          <div
            v-for="(item, index) in records"
            :key="`${item.jobId}-${item.createTime}-${index}`"
            class="record-item"
            :class="{ active: selectedRecord === item }"
            @click="handleSelect(item)"
          >
            <span class="record-index">{{ index + 1 }}</span>
            <span class="record-time">{{ formatTime(item.createTime) }}</span>
          </div>
        </div>
      </ApboaSpin>
    </template>

    <!-- 节点执行详情视图 -->
    <template v-else-if="viewMode === 'workflow-detail'">
      <div class="detail-header">
        <AButton type="text" @click="handleBack">
          <template #icon><ArrowLeftOutlined /></template>
          返回记录列表
        </AButton>
        <span class="detail-time">{{ formatTime(String(selectedRecord?.createTime)) }}</span>
      </div>

      <ApboaSpin :spinning="nodesLoading">
        <div v-if="nodeExecutions.length === 0 && !nodesLoading" class="empty-state">
          <AEmpty description="暂无节点执行日志" />
        </div>

        <div v-else class="execution-list">
          <div
            v-for="(item, index) in nodeExecutions"
            :key="item.id || `${item.nodeId}-${index}`"
            class="execution-item"
          >
            <div class="execution-head">
              <div class="execution-copy">
                <span class="execution-title">{{ executionTitle(item, index) }}</span>
                <span class="execution-meta">{{ item.nodeType }} · {{ nodeDuration(item.startTime, item.endTime) }}</span>
              </div>
              <ATooltip :title="statusText(item.status)">
                <CheckCircleFilled v-if="item.status === 'SUCCESS'" style="color: #52c41a; font-size: 16px;" />
                <CloseCircleFilled v-else-if="item.status === 'FAIL'" style="color: #ff4d4f; font-size: 16px;" />
                <PlayCircleFilled v-else-if="item.status === 'RUNNING'" style="color: #1677ff; font-size: 16px;" />
                <ExclamationCircleFilled v-else style="color: #8c8c8c; font-size: 16px;" />
              </ATooltip>
            </div>
            <ACollapse ghost size="small">
              <ACollapsePanel v-if="item.error && item.error !== '{}'" key="error" header="错误">
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
      </ApboaSpin>
    </template>

    <!-- 智能体对话详情视图 -->
    <template v-else-if="viewMode === 'agent-detail'">
      <div class="detail-header">
        <AButton type="text" @click="handleBack">
          <template #icon><ArrowLeftOutlined /></template>
          返回记录列表
        </AButton>
        <span class="detail-time">{{ formatTime(String(selectedRecord?.createTime)) }}</span>
      </div>

      <ApboaSpin :spinning="agentMessagesLoading">
        <div v-if="visibleAgentMessages.length === 0 && !agentMessagesLoading" class="empty-state">
          <AEmpty description="暂无对话内容" />
        </div>

        <MessageList v-else :messages="visibleAgentMessages" />
      </ApboaSpin>
    </template>
  </ADrawer>
</template>

<style scoped lang="scss">
.empty-state {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.records-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover,
  &.active {
    background-color: rgba(0, 0, 0, 0.02);
  }
}

.record-index {
  font-size: 12px;
  color: #999;
  min-width: 20px;
}

.record-time {
  font-size: 14px;
  color: #333;
}

/* 节点详情视图 */
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-time {
  font-size: 13px;
  color: #8c8c8c;
}

.execution-list {
  display: grid;
  gap: 10px;
}

.execution-item {
  border-radius: 8px;
  background: #F2F4F7;
  padding-bottom: 10px;
  overflow: hidden;
}

.execution-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
}

.execution-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.execution-title {
  color: #262626;
  font-weight: 700;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.execution-meta {
  color: #8c8c8c;
  font-size: 12px;
}

.json-pre {
  margin: 0;
  padding: 8px 12px;
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  color: #262626;
  font-size: 12px;
  line-height: 1.6;
}

.json-pre.compact {
  min-height: auto;
  max-height: 220px;
  background: #fcfcfc;
}

:deep(.ant-collapse-header) {
  padding: 4px 10px !important;
}

:deep(.ant-collapse-content-box) {
  padding: 4px 10px 4px 32px !important;
}
</style>

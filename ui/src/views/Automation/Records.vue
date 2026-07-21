/**
 * 自动化任务执行记录页面
 * 左侧记录列表 + 右侧详情展示（工作流节点日志 / 智能体对话）
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  ArrowLeftOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  PlayCircleFilled,
  ExclamationCircleFilled,
  HistoryOutlined,
} from '@ant-design/icons-vue'
import { Collapse as ACollapse, CollapsePanel as ACollapsePanel } from 'ant-design-vue'
import * as automationApi from '@/api/automation'
import { workflowRunNodes } from '@/api/workflow'
import * as chatSessionApi from '@/api/chatSession'
import MessageList from '@/components/chatHistory/MessageList.vue'
import agentAvatar from '@/assets/avatar/agent.png'
import workflowAvatar from '@/assets/avatar/workflow.png'
import type { JobInfo } from '@/types'
import type { JobRecordVO } from '@/types/automation'
import type { WorkflowNodeExecution } from '@/types/workflow'
import type { ChatMessageVO, DisplayMessage } from '@/types'

const route = useRoute()
const router = useRouter()

const job = ref<JobInfo | null>(null)
const jobLoading = ref(false)

const records = ref<JobRecordVO[]>([])
const recordsLoading = ref(false)
const selectedRecord = ref<JobRecordVO | null>(null)

const nodesLoading = ref(false)
const nodeExecutions = ref<WorkflowNodeExecution[]>([])

const agentMessagesLoading = ref(false)
const agentMessages = ref<ChatMessageVO[]>([])

/** 从 dataMap 中解析任务名称 */
const jobDisplayName = computed(() => {
  if (!job.value) return '执行记录'
  try {
    const dataMap = JSON.parse(job.value.dataMap || '{}')
    return dataMap.jobName || '执行记录'
  } catch {
    return '执行记录'
  }
})

/** 根据任务类型获取头像 */
const avatarSrc = computed(() => {
  if (job.value?.type === 'WORKFLOW') return workflowAvatar
  return agentAvatar
})

/** 当前详情类型：workflow 或 agent */
const detailType = computed<'workflow' | 'agent' | null>(() => {
  if (!selectedRecord.value) return null
  if (job.value?.type === 'WORKFLOW') return 'workflow'
  if (job.value?.type === 'AGENT') return 'agent'
  return null
})

/** 获取任务信息 */
async function loadJob() {
  const id = route.params.id as string
  if (!id) return
  jobLoading.value = true
  try {
    const res = await automationApi.getJobById(id)
    job.value = res.data.data
  } catch (e) {
    console.error('加载任务信息失败:', e)
  } finally {
    jobLoading.value = false
  }
}

/** 加载记录列表 */
async function loadRecords() {
  const id = route.params.id as string
  if (!id) return
  recordsLoading.value = true
  try {
    const res = await automationApi.getRecords(id)
    records.value = res.data.data || []
  } catch (e) {
    console.error('加载运行记录失败:', e)
  } finally {
    recordsLoading.value = false
  }
}

/** 加载工作流节点执行详情 */
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

/** 加载智能体会话消息 */
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

function handleSelect(record: JobRecordVO) {
  selectedRecord.value = record
  if (!record.recordId) return
  if (job.value?.type === 'WORKFLOW') {
    loadNodeExecutions(record.recordId)
  } else if (job.value?.type === 'AGENT') {
    loadAgentMessages(record.recordId)
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
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

function goBack() {
  router.push('/automation')
}

onMounted(async () => {
  await loadJob()
  loadRecords()
})
</script>

<template>
  <div class="records-page">
    <!-- 左侧记录列表 -->
    <aside class="records-sidebar">
      <div class="sidebar-header">
        <AButton type="text" @click="goBack">
          <template #icon><ArrowLeftOutlined /></template>
        </AButton>
        <span class="sidebar-header-title">
          <span class="sidebar-header-text" :title="jobDisplayName">{{ jobDisplayName }}</span>
        </span>
      </div>

      <ApboaSpin :spinning="recordsLoading" class="sidebar-body">
        <div v-if="records.length === 0 && !recordsLoading" class="sidebar-empty">
          <AEmpty description="暂无运行记录" />
        </div>

        <div v-else class="records-list">
          <div
            v-for="(item, index) in records"
            :key="`${item.jobId}-${item.createTime}-${index}`"
            class="record-item"
            :class="{ active: selectedRecord === item }"
            @click="handleSelect(item)"
          >
            <span class="record-index">{{ index + 1 }}</span>
            <img :src="avatarSrc" class="sidebar-header-avatar" />
            <span class="record-time">{{ formatTime(item.createTime) }}</span>
          </div>
        </div>
      </ApboaSpin>
    </aside>

    <!-- 右侧详情区域 -->
    <main class="records-main">
        <!-- 未选中记录时的引导 -->
        <div v-if="!selectedRecord" class="guide-placeholder">
          <HistoryOutlined class="guide-icon" />
          <p class="guide-text">点击左侧记录查看详情</p>
        </div>

        <!-- 工作流节点详情 -->
        <template v-else-if="detailType === 'workflow'">
          <ApboaSpin :spinning="nodesLoading">
            <div v-if="nodeExecutions.length === 0 && !nodesLoading" class="detail-empty">
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

        <!-- 智能体对话详情 -->
        <template v-else-if="detailType === 'agent'">
          <ApboaSpin :spinning="agentMessagesLoading">
            <div v-if="visibleAgentMessages.length === 0 && !agentMessagesLoading" class="detail-empty">
              <AEmpty description="暂无对话内容" />
            </div>

            <MessageList v-else :messages="visibleAgentMessages" />
          </ApboaSpin>
        </template>
    </main>
  </div>
</template>

<script lang="ts">
export default {
  name: 'AutomationRecordsView'
}
</script>

<style scoped lang="scss">
.records-page {
  display: flex;
  height: 100%;
  background: #fff;
}

/* ---- 左侧列表 ---- */
.records-sidebar {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid #e8e8e8;
}

.sidebar-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  height: 48px;
}

.sidebar-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #262626;
}

.sidebar-header-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-header-avatar {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  flex-shrink: 0;
}

.sidebar-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.sidebar-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  padding: 60px 0;
}

.records-list {
  height: 100%;
  overflow-y: auto;
  padding: 8px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover,
  &.active {
    background-color: rgba(0, 0, 0, 0.03);
  }
}

.record-index {
  font-size: 12px;
  color: #999;
  min-width: 20px;
}

.record-time {
  font-size: 13px;
  color: #333;
}

/* ---- 右侧详情 ---- */
.records-main {
  flex: 1;
  min-width: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.guide-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 16px;
}

.guide-icon {
  font-size: 48px;
  color: #d9d9d9;
}

.guide-text {
  font-size: 14px;
  color: #999;
  margin: 0;
}

.detail-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-head-title {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
}

.detail-head-time {
  font-size: 13px;
  color: #8c8c8c;
}

.detail-empty {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

/* ---- 节点日志 ---- */
.execution-list {
  display: grid;
  gap: 10px;
  padding: 20px 24px;
}

.execution-item {
  border-radius: 8px;
  background: #F7F7F7;
  padding-bottom: 10px;
  overflow: hidden;
}

.execution-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
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
  padding: 4px 14px !important;
}

:deep(.ant-collapse-content-box) {
  padding: 4px 14px 4px 36px !important;
}
</style>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import { useRoute } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { useAccountStore, useChatStore } from '@/stores'
import { formatSessionTitle } from '@/utils/chat/format'
import { useAgentDetail } from '@/composables/chat/useAgentDetail'
import { useSessions } from '@/composables/chat/useSessions'
import { useCurrentSession } from '@/composables/chat/useCurrentSession'
import { useChatStream } from '@/composables/chat/useChatStream'
import ChatSidebar from '@/components/chat/ChatSidebar.vue'
import ChatMain from '@/components/chat/ChatMain.vue'
import RenameModal from '@/components/chat/RenameModal.vue'
import WorkspacePanel from '@/components/workspace/WorkspacePanel.vue'
import type { DisplayMessage, ChatMessageVO, UploadedFileItem, ChatSessionVO } from '@/types'
import * as chatSessionApi from '@/api/chatSession'
import { getActiveRuns, getStatus } from '@/api/agui'
import { LoadingOutlined } from '@ant-design/icons-vue'
import {
  buildUserTextFromPayload,
  injectSubmissionToRawContent
} from '@/utils/chat/uip.ts'
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'

const props = withDefaults(defineProps<{
  showAccount: boolean
  chatAgentId: string | null | undefined
}>(), {
  showAccount: true,
  chatAgentId: null
})

const route = useRoute()
const accountStore = useAccountStore()
const chatStore = useChatStore()
const userInfo = computed(() => accountStore.userInfo)

const agentId = computed(() => (props.chatAgentId || route.params.agentId) as string || '')

// 智能体详情
const { agentDetail, allowFileType } = useAgentDetail(agentId)

// 记忆/规划是否可用（由 agentDetail 决定）
const accountId = computed(() => accountStore.userInfo?.id)
const enableMemory = computed(() => agentDetail.value?.enableMemory === true)
const enablePlanning = computed(() => agentDetail.value?.enablePlanning === true)
const showToolProcess = computed(() => agentDetail.value?.showToolProcess === true)
// 是否配置了代码执行
const hasCodeExecutionConfig = computed(() => agentDetail.value?.codeExecutionConfigId)

// 记忆/规划/侧边栏状态：从 Pinia store 读取（持久化由 pinia-plugin-persistedstate 处理）
const memoryActive = computed(() => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.preferences // 依赖以保持响应性
  return chatStore.getMemoryActive(id as string, accountId.value as string, enableMemory.value)
})
const planActive = computed(() => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.preferences
  return chatStore.getPlanActive(id as string, accountId.value as string, enablePlanning.value)
})
const toolProcessActive = computed(() => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.preferences
  return chatStore.getToolProcessActive(id as string, accountId.value as string, showToolProcess.value)
})
const sidebarCollapsed = computed({
  get: () => {
    const id = agentDetail.value?.id ?? agentId.value
    chatStore.preferences
    return chatStore.getSidebarCollapsed(id as string, accountId.value as string)
  },
  set: (v: boolean) => {
    const id = agentDetail.value?.id ?? agentId.value
    chatStore.setSidebarCollapsed(id as string, accountId.value as string, v)
  },
})

const handleMemoryChange = (v: boolean) => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.setMemoryActive(id as string, accountId.value as string, v)
}

const handlePlanChange = (v: boolean) => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.setPlanActive(id as string, accountId.value as string, v)
}

const handelToolProcess = (v: boolean) => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.setToolProcessActive(id as string, accountId.value as string, v)
}

// 会话列表管理
const {
  pinnedSessions,
  otherSessions,
  loading: sessionsLoading,
  hasMore: sessionsHasMore,
  createSession,
  updateSessionTitle,
  pinSession,
  unpinSession,
  deleteSession,
  loadSessions,
  loadMoreSessions,
} = useSessions(agentId)

// 当前会话管理
const {
  currentSessionId,
  currentSessionTitle,
  currentSessionMessageTable,
  messagesList,
  hasMoreHistory,
  historyLoading,
  selectSession,
  resetSession,
  loadMoreHistory,
  loadingMessages
} = useCurrentSession(agentId)

// 已上传附件（仅已完成上传的计入 fileIds）
const uploadedFiles = ref<UploadedFileItem[]>([])
const fileIds = computed(() =>
  uploadedFiles.value.filter((f) => !f.uploading).map((f) => f.id)
)

// 流式对话及工具调用
const {
  agentHasResult,
  streamingContent,
  streamingMessageId,
  streamingRole,
  toolCallsInProgress,
  isRunning,
  currentPlan,
  sendMessage,
  sendToolContent,
  abortRun,
  reconnect: reconnectStream,
  disconnect: disconnectStream,
  resetStreamingState,
} = useChatStream(
  agentId,
  agentDetail,
  currentSessionId,
  fileIds,
  memoryActive,
  planActive,
  toolProcessActive,
  (chatMsg: ChatMessageVO) => {
    messagesList.value.push(chatMsg)
  })

// 输入框内容
const inputText = ref('')

// 记录最近一次流式消息的 ID，用于 DOM key 桥接，避免流式→保存切换时的闪烁
const lastStreamingKey = ref<string | null>(null)

watch(streamingMessageId, (newId) => {
  if (newId) {
    lastStreamingKey.value = newId
  }
})

/** 构建文件前缀字符串 */
function buildFilesPrefix(files: UploadedFileItem[]): string {
  if (!files.length) return ''
  return JSON.stringify({ files }) + '@==##::::##==@'
}

// 构建展示消息
const displayMessages = computed<DisplayMessage[]>(() => {
  const list: DisplayMessage[] = []
  for (let i = 0; i < messagesList.value.length; i++) {
    const m = messagesList.value[i]
    if (!m || m.role === 'system' || !m.content) continue

    let displayId = String(m.id)
    // key 桥接：流式刚结束时，将最后一条 assistant 消息的展示 key 替换为流式 ID
    if (!streamingMessageId.value && lastStreamingKey.value && m.role === 'assistant') {
      const hasLaterAssistant = messagesList.value.slice(i + 1).some(x => x.role === 'assistant')
      if (!hasLaterAssistant) {
        displayId = lastStreamingKey.value
      }
    }

    list.push({
      id: displayId,
      role: m.role as DisplayMessage['role'],
      content: m.content || '',
      createdAt: m.createdAt,
      isStreaming: false,
    })
  }

  if (streamingMessageId.value) {
    list.push({
      id: streamingMessageId.value,
      role: streamingRole.value,
      content: streamingContent.value,
      isStreaming: true,
    })
  }  else {
    // 响应加载动画（没有任何推理或文本内容时）
    if (list[list.length - 1]?.role === 'user') {
      list.push({
        id: '',
        role: 'assistant',
        content: '',
        isStreaming: true,
      })
    }
  }
  return list
})

// 重命名模态框
const renameModalVisible = ref(false)
const renameSessionRef = ref<any>(null)
const renameTitle = ref('')
const renameSubmitting = ref(false)

// 新会话
const handleNewSession = async () => {
  // 断开当前 SSE 连接，旧会话继续后台运行
  preserveRunningSession.value = true
  disconnectStream()

  // 开启工作空间的情况特殊处理
  if (hasCodeExecutionConfig.value) {
    if (currentSessionTitle.value === '新对话') {
      preserveRunningSession.value = false
      return
    }

    const existNewSession = otherSessions.value.find((s) => s.title === '新对话')
    if (existNewSession) {
      await selectSession({
        id: existNewSession.id,
        title: existNewSession.title || '新对话',
      } as ChatSessionVO)

      preserveRunningSession.value = false
      return
    }

    const newSession = await createSession(formatSessionTitle(null), true)
    if (!newSession) {
      preserveRunningSession.value = false
      return
    }
    resetSession({
      id: String(newSession.id),
      title:'新对话'
    } as ChatSessionVO)
  } else {
    resetSession(null)
  }

  preserveRunningSession.value = false
}

// 选择会话
const handleSelectSession = async (session: ChatSessionVO) => {
  // 切换前断开当前 SSE，但不中断后台 Agent
  preserveRunningSession.value = true
  disconnectStream()
  resetStreamingState()
  await selectSession(session)
  // 如果目标会话在运行中，触发重连
  if (runningSessions.value.has(String(session.id))) {
    // 注意：不要加 await，否则会阻塞会话切换
    reconnectStream(String(session.id))
  }
  preserveRunningSession.value = false
}

// 会话菜单操作
const handleSessionMenu = async (key: string, session: ChatSessionVO) => {
  const id = String(session.id)
  if (key === 'rename') {
    renameSessionRef.value = session
    renameTitle.value = session.title || '新对话'
    renameModalVisible.value = true
    return
  }
  if (key === 'pin') {
    await pinSession(id)
    if (currentSessionId.value === id) {
      // 若当前会话被置顶，可能需要更新列表，已自动重新加载
    }
    return
  }
  if (key === 'unpin') {
    await unpinSession(id)
    return
  }
  if (key === 'delete') {
    if (isRunning.value) {
      message.info('请等待当前对话完成')
      return
    }
    Modal.confirm({
      title: '确认删除',
      content: '删除后无法恢复，是否继续？',
      onOk: async () => {
        await deleteSession(id)
        if (currentSessionId.value === id) {
          resetSession(null)
        }
      },
    })
    return
  }
}

// 提交重命名
const submitRename = async () => {
  const session = renameSessionRef.value
  if (!session) return
  const title = renameTitle.value.trim() || '新对话'
  renameSubmitting.value = true
  try {
    await updateSessionTitle(session.id, title)
    renameModalVisible.value = false
  } finally {
    renameSubmitting.value = false
  }
}

// 发送工具执行结果
const handelToolContent = async (value: any) => {
  await sendToolContent(value)
}

// 处理交互提交
const handleInteractionSubmit = async (payload: InteractionSubmitPayload) => {
  if (!currentSessionId.value || isRunning.value) return

  const sid = currentSessionId.value
  const data = payload.data as Record<string, unknown>

  // 1. 找到对应的 assistant 消息，将 submittedData 注入 UIP JSON（单次调用完成匹配+注入）
  let updatedContent: string | null = null
  const assistantMsg = [...messagesList.value].reverse().find(m => {
    if (m.role !== 'assistant' || !m.content) return false
    const updated = injectSubmissionToRawContent(m.content, payload.interactionId, data)
    if (updated !== m.content) {
      updatedContent = updated
      return true
    }
    return false
  })
  if (assistantMsg && updatedContent) {
    // 持久化到后端（后端通过 session 的 current_message_id 定位消息，无需传 messageId）
    chatSessionApi.updateCurrentMessageContent(sid, updatedContent)
      .catch(err => console.warn('[UIP] 持久化提交数据失败', err))
    // 同步更新本地消息列表，使渲染器立即读取 submittedData
    assistantMsg.content = updatedContent
  }

  // // 2. 保存用户提交消息到 DB（与 handleSend 保持一致，先 await 再 sendMessage）
  // try {
  //   const res = await chatSessionApi.appendMessage(sid, { role: 'user', content: userText })
  //   if (res.data?.data) messagesList.value.push(res.data.data as ChatMessageVO)
  // } catch (err) {
  //   console.warn('[UIP] 保存用户提交消息失败', err)
  // }

  // 3. 发送给 Agent 继续对话
  const userText = buildUserTextFromPayload(payload)
  await sendMessage(userText, [{ id: 'uip', role: 'user', content: userText }] as ChatMessageVO[])
}

// 处理 UIP 卡片渲染失败重试
const handleUIPRetry = async (uipCode: string) => {
  if (!currentSessionId.value || isRunning.value) return

  // 构造重试消息：提示文本 + 原始 UIP 内容，让智能体参考修正
  const retryText = `上一条消息中的交互卡片生成有误，请重新生成。\n\n原始卡片内容：\n${uipCode}`

  await sendMessage(retryText, [{ id: 'uip', role: 'user', content: retryText }] as ChatMessageVO[])
}

// 处理 VEP 视觉卡片渲染失败重试
const handleVEPRetry = async (vepCode: string) => {
  if (!currentSessionId.value || isRunning.value) return

  // 构造重试消息：提示文本 + 原始 VEP 内容，让智能体参考修正
  const retryText = `上一条消息中的视觉卡片生成有误，请重新生成。\n\n原始卡片内容：\n${vepCode}`

  await sendMessage(retryText, [{ id: 'vep', role: 'user', content: retryText }] as ChatMessageVO[])
}

// 发送消息
const handleSend = async () => {
  const text = inputText.value.trim()
  const filesToSend = uploadedFiles.value.filter((f) => !f.uploading)
  const hasFiles = filesToSend.length > 0
  if ((!text && !hasFiles) || !agentId.value || isRunning.value) return

  const finalText = hasFiles ? buildFilesPrefix(filesToSend) + text : text
  const fileIdsToSend = filesToSend.map((f) => f.id)

  // 立即清空输入框和附件，提升交互体验
  inputText.value = ''
  uploadedFiles.value = []

  // 如果没有当前会话，先创建
  if (!currentSessionId.value) {
    const titleInput = text || '新对话'
    const newSession = await createSession(formatSessionTitle(titleInput))
    if (!newSession) return
    currentSessionId.value = String(newSession.id)
    currentSessionTitle.value = newSession.title || '新对话'
  }

  // 保存用户消息
  const userMsg = await chatSessionApi.appendMessage(currentSessionId.value, { role: 'user', content: finalText })
  // 如果是新会话，更新标题
  if (messagesList.value.length <= 1) {
    const title = formatSessionTitle(text || '新对话')
    await updateSessionTitle(currentSessionId.value, title)
    currentSessionTitle.value = title
  }
  messagesList.value.push(userMsg.data.data)

  // 触发流式回复（传入 fileIdsToSend，因输入框已提前清空）
  await sendMessage(
    finalText,
    [{ role: 'user', content: finalText }] as ChatMessageVO[],
    fileIdsToSend
  )
}

// 切换侧边栏（通过 computed setter 自动持久化到 store）
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

/** 工作空间面板开关状态 */
const workspacePanelOpen = ref(false)

/** 工作空间面板引用（供外部调用 startFileOperation 等） */
const workspacePanelRef = ref<InstanceType<typeof WorkspacePanel> | null>(null)

/**
 * 切换工作空间面板显示/隐藏
 */
const toggleWorkspace = () => {
  workspacePanelOpen.value = !workspacePanelOpen.value
}

// 跟踪所有后台运行中的会话 ID
const runningSessions = ref<Set<string>>(new Set())

/** 临时标志：会话切换/新建期间，阻止 watch(isRunning) 误删旧 session 的运行态 */
const preserveRunningSession = ref(false)

// 后台会话状态轮询（5s 间隔）
let pollingTimer: ReturnType<typeof setInterval> | null = null

const startPolling = () => {
  if (pollingTimer) return
  pollingTimer = setInterval(async () => {
    if (runningSessions.value.size === 0) {
      stopPolling()
      return
    }
    for (const tid of runningSessions.value) {
      if (tid === currentSessionId.value) continue
      try {
        const running = await getStatus(tid)
        if (!running) {
          const next = new Set(runningSessions.value)
          next.delete(tid)
          runningSessions.value = next
        }
      } catch {
        // 忽略网络错误
      }
    }
  }, 5000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 初始化：获取活跃运行列表，若当前会话在运行则重连
onMounted(async () => {
  loadSessions()
  try {
    const activeIds = await getActiveRuns()
    runningSessions.value = new Set(activeIds)
    if (currentSessionId.value && activeIds.includes(currentSessionId.value)) {
      reconnectStream(currentSessionId.value)
    }
    if (activeIds.length > 0) {
      startPolling()
    }
  } catch {
    // 忽略初始化错误
  }
})

// 组件卸载时清理 poll timer
onBeforeUnmount(() => {
  stopPolling()
  disconnectStream()
})

// 运行状态变化时更新 runningSessions
watch(isRunning, (running) => {
  const sid = currentSessionId.value
  if (!sid) return
  const next = new Set(runningSessions.value)
  if (running) {
    next.add(sid)
    startPolling()
  } else {
    if (!preserveRunningSession.value) {
      next.delete(sid)
    }
  }
  runningSessions.value = next
})
</script>

<template>
  <div class="chat-page" style="background-color: #ffffff">
    <ChatSidebar
      :collapsed="sidebarCollapsed"
      :agent-name="agentDetail?.name"
      :pinned-sessions="pinnedSessions"
      :other-sessions="otherSessions"
      :current-session-id="currentSessionId"
      :running-sessions="runningSessions"
      :user-nickname="userInfo?.nickname"
      :loading="sessionsLoading"
      :has-more="sessionsHasMore"
      :show-account="showAccount"
      @toggle-collapse="toggleSidebar"
      @new-session="handleNewSession"
      @select-session="handleSelectSession"
      @session-menu="handleSessionMenu"
      @load-more="loadMoreSessions"
    />

    <RenameModal
      v-model:visible="renameModalVisible"
      v-model:title="renameTitle"
      :confirm-loading="renameSubmitting"
      @ok="submitRename"
    />

    <div v-if="loadingMessages" class="loading-messages"><LoadingOutlined style="margin-right: 6px" />加载中</div>
    <ChatMain
      v-else
      ref="chatMainRef"
      :title="currentSessionTitle || agentDetail?.name || '对话'"
      :message-size="messagesList.length"
      :welcome-headline="`来和 ${agentDetail?.name || '智能体'} 聊聊吧`"
      :welcome-desc="agentDetail?.description || '有什么想说的，直接发给我就好～'"
      :messages="displayMessages"
      :tool-calls="toolCallsInProgress"
      :input-value="inputText"
      :uploaded-files="uploadedFiles"
      :isRunning="isRunning"
      :agent-id="agentId"
      :memory-active="memoryActive"
      :plan-active="planActive"
      :enable-memory="enableMemory"
      :enable-planning="enablePlanning"
      :allow-upload-file-type="allowFileType"
      :agent-has-result="agentHasResult"
      :show-tool-process="showToolProcess"
      :tool-process-active="toolProcessActive"
      :workspace-panel-open="workspacePanelOpen"
      :has-code-execution-config="!!hasCodeExecutionConfig"
      :session-id="currentSessionId"
      :session-message-table="currentSessionMessageTable"
      :has-more-history="hasMoreHistory"
      :history-loading="historyLoading"
      :current-plan="currentPlan"
      @update:input-value="inputText = $event"
      @update:uploaded-files="uploadedFiles = $event"
      @memory="handleMemoryChange"
      @plan="handlePlanChange"
      @toolProcess="handelToolProcess"
      @toolContent="handelToolContent"
      @send="handleSend"
      @abort="abortRun"
      @toggle-sidebar="toggleSidebar"
      @toggle-workspace="toggleWorkspace"
      @load-more-history="loadMoreHistory"
      @new-session="handleNewSession"
      @plan-destroyed="currentPlan = null"
      @interaction-submit="handleInteractionSubmit"
      @uip-retry="handleUIPRetry"
      @vep-retry="handleVEPRetry"
    />
    <!-- 工作空间面板（作为 flex 子项从右侧滑出） -->
    <WorkspacePanel
      v-if="currentSessionId && !!hasCodeExecutionConfig && !loadingMessages"
      ref="workspacePanelRef"
      :session-id="currentSessionId"
      :class="{ open: workspacePanelOpen }"
      @close="workspacePanelOpen = false"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;
.loading-messages {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { useAccountStore, useChatStore } from '@/stores'
import { formatSessionTitle } from '@/utils/chat/format'
import { useAgentDetail } from '@/composables/chat/useAgentDetail'
import { useSessions } from '@/composables/chat/useSessions'
import { useCurrentSession } from '@/composables/chat/useCurrentSession'
import { useChatStream } from '@/composables/chat/useChatStream'
import { RouteNames } from '@/router'
import ChatSidebar from '@/components/chat/ChatSidebar.vue'
import ChatMain from '@/components/chat/ChatMain.vue'
import RenameModal from '@/components/chat/RenameModal.vue'
import WorkspacePanel from '@/components/workspace/WorkspacePanel.vue'
import type { DisplayMessage, ChatMessageVO, UploadedFileItem, ChatSessionVO } from '@/types'
import * as chatSessionApi from '@/api/chatSession'
import type { ConfirmMode } from '@/api/chatSession'
import { getActiveRuns, getStatus, getPending, getSubagentPending } from '@/api/agui'
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
const router = useRouter()
const accountStore = useAccountStore()
const chatStore = useChatStore()
const userInfo = computed(() => accountStore.userInfo)

const agentId = computed(() => (props.chatAgentId || route.params.agentId) as string || '')

// 智能体详情
const { agentDetail, agentAvatar, allowFileType } = useAgentDetail(agentId)

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

// 常驻常用问题折叠状态（per agent+account 持久化偏好）
const commonQuestionsCollapsed = computed(() => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.preferences
  return chatStore.getCommonQuestionsCollapsed(id as string, accountId.value as string)
})

const handleToggleQuestionsCollapsed = () => {
  const id = agentDetail.value?.id ?? agentId.value
  chatStore.setCommonQuestionsCollapsed(id as string, accountId.value as string, !commonQuestionsCollapsed.value)
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

// 顶部标题：无实义标题（未创建会话回退智能体名 / 占位"新对话"）时隐藏
const headerTitle = computed(() => {
  const t = currentSessionTitle.value
  return (!t || t === '新对话') ? '' : t
})

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
  decideConfirm,
  pendingConfirms,
  restorePending,
  decideSubConfirm,
  pendingSubConfirms,
  restoreSubPending,
  decideAllSubPending,
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
  },
  // RUN_META（run 收尾实时下发，与落库 meta 同构）：回填最后一条 assistant 消息，
  // 前端流式结束即最终态，无需补拉查库
  (meta) => {
    const lastAssistant = [...messagesList.value].reverse().find((m) => m.role === 'assistant')
    if (lastAssistant) {
      lastAssistant.meta = JSON.stringify(meta)
    }
  })

// HITL 授权模式（会话级三态，source of truth 在 Redis：换端/刷新一致；无记录=逐步确认）
const confirmMode = ref<ConfirmMode>('MANUAL')
// 新会话尚未创建（懒创建，首条消息才有 sessionId）时的本地暂存：
// 会话创建后由 watch 回放写入 Redis（null 不回放）
let pendingConfirmMode: ConfirmMode | null = null

/** 归一化接口返回的模式值，未知回退 MANUAL */
const normalizeMode = (m: unknown): ConfirmMode =>
  m === 'AUTO_APPROVE' || m === 'AUTO_REJECT' ? m : 'MANUAL'

// 加载中间态只可能发生在首次触发（刷新/URL 直达时 currentSessionId 短暂为 null）；
// 运行期的置空（点开启新对话、删除当前会话）都是主动进入新对话——URL 里残留的旧
// sessionId 不代表加载中，不得据其误判成 MANUAL（否则从旧会话点新对话默认变逐步确认）
let firstConfirmModeWatch = true

// 会话切换/进入时拉取模式；读取失败按逐步确认兜底
watch(currentSessionId, async (sid) => {
  const isFirstWatch = firstConfirmModeWatch
  firstConfirmModeWatch = false
  if (!sid) {
    // 新对话预设默认一键授权（免每次手动开），本地先行生效，首条消息创建会话后由
    // 暂存回放写入 Redis。仅「首次触发且 URL 带会话」是刷新/直达的加载中间态，不预设——
    // 否则暂存回放会把该会话的既有模式强制翻转，恢复中的挂起确认被误放行/误拒
    const isLoadingExistingSession = isFirstWatch && !!route.params.sessionId
    confirmMode.value = isLoadingExistingSession ? 'MANUAL' : 'AUTO_APPROVE'
    pendingConfirmMode = isLoadingExistingSession ? null : 'AUTO_APPROVE'
    return
  }
  if (pendingConfirmMode) {
    // 有暂存（无会话状态下用户已选模式）：回放写入并跳过 GET，避免 GET 的默认值冲掉预设
    const mode = pendingConfirmMode
    pendingConfirmMode = null
    try {
      await chatSessionApi.setConfirmMode(sid, mode)
      confirmMode.value = mode
    } catch {
      confirmMode.value = 'MANUAL'
    }
    return
  }
  try {
    const res = await chatSessionApi.getConfirmMode(sid)
    confirmMode.value = normalizeMode(res.data?.data)
  } catch {
    confirmMode.value = 'MANUAL'
  }
}, { immediate: true })

/** 把当前所有待确认工具统一决策（一键授权→全批 / 拒绝授权→全拒；凑齐决策后 useChatStream 自动提交 resume） */
const decideAllPending = (approved: boolean) => {
  Object.entries(pendingConfirms.value).forEach(([toolUseId, state]) => {
    if (state === 'pending') decideConfirm(toolUseId, approved)
  })
}

const handleConfirmModeChange = (mode: ConfirmMode) => {
  const sid = currentSessionId.value
  if (!sid) {
    // 会话未创建：本地先行生效，首条消息创建会话后由 watch 回放写入 Redis
    confirmMode.value = mode
    pendingConfirmMode = mode
    return
  }
  chatSessionApi.setConfirmMode(sid, mode).then(() => {
    confirmMode.value = mode
    // 已弹出的待确认项按新模式一并处置（主流程 + 子智能体挂起中的）；逐步确认保持等待
    if (mode === 'AUTO_APPROVE') {
      decideAllPending(true)
      decideAllSubPending(true)
    } else if (mode === 'AUTO_REJECT') {
      decideAllPending(false)
      decideAllSubPending(false)
    }
  })
}

// 兜底：自动模式下任何新到达的待确认项（跑动中切模式的竞态窗口、刷新恢复的遗留 pending）
// 按模式自动处置：一键授权全批 / 拒绝授权全拒
watch(pendingConfirms, (confirms) => {
  if (confirmMode.value === 'MANUAL') return
  if (Object.values(confirms).some((s) => s === 'pending')) {
    decideAllPending(confirmMode.value === 'AUTO_APPROVE')
  }
})

// 同款兜底：子智能体挂起中确认（后端已自动处置新触发的，这里兜住「逐步确认模式
// 挂起后、用户中途切换模式」的存量批次）
watch(pendingSubConfirms, (batches) => {
  if (confirmMode.value === 'MANUAL') return
  const hasPending = Object.values(batches).some((b) =>
    Object.values(b.decisions).some((s) => s === 'pending')
  )
  if (hasPending) {
    decideAllSubPending(confirmMode.value === 'AUTO_APPROVE')
  }
})

// ========== 会话级思考模式（仅 thinkingSwitchSupported 的模型展示按钮） ==========
// 有效值 = Redis 覆盖 ?? 默认开；切换写覆盖值，下一条消息生效（后端检测变化重建 agent）
const thinkingSupported = computed(() => agentDetail.value?.thinkingSwitchSupported === true)
const thinkingActive = ref(true)
// 会话未创建时的本地暂存（复刻 pendingConfirmMode 模式）：创建后回放写入
let pendingThinkingMode: boolean | null = null

// 双源触发：会话切换 + thinkingSupported 就绪（agent 详情异步加载可能晚于会话 watch，
// 只依赖会话会在刷新进入"已关思考"的会话时跳过 GET，按钮显示默认开与实际不符）
watch([currentSessionId, thinkingSupported], async ([sid]) => {
  if (!sid) {
    // 新会话默认开思考；刷新/直达加载中间态同样显示默认，稍后 GET 校正
    thinkingActive.value = true
    pendingThinkingMode = null
    return
  }
  if (pendingThinkingMode !== null) {
    const v = pendingThinkingMode
    pendingThinkingMode = null
    try {
      await chatSessionApi.setThinkingMode(sid, v)
      thinkingActive.value = v
    } catch {
      thinkingActive.value = true
    }
    return
  }
  if (!thinkingSupported.value) return
  try {
    const res = await chatSessionApi.getThinkingMode(sid)
    thinkingActive.value = res.data?.data !== false
  } catch {
    thinkingActive.value = true
  }
}, { immediate: true })

const handleThinkingChange = (v: boolean) => {
  const sid = currentSessionId.value
  if (!sid) {
    thinkingActive.value = v
    pendingThinkingMode = v
    return
  }
  chatSessionApi.setThinkingMode(sid, v).then(() => {
    thinkingActive.value = v
  })
}

// 输入框内容
const inputText = ref('')

// 记录最近一次流式消息的 ID，用于 DOM key 桥接，避免流式→保存切换时的闪烁
const lastStreamingKey = ref<string | null>(null)

watch(streamingMessageId, (newId) => {
  if (newId) {
    lastStreamingKey.value = newId
  }
})

/** 构建文件前缀字符串（剔除 localUrl：objectURL 是会话内存态临时地址，不得落库） */
function buildFilesPrefix(files: UploadedFileItem[]): string {
  if (!files.length) return ''
  const persistable = files.map(({ localUrl: _localUrl, ...rest }) => rest)
  return JSON.stringify({ files: persistable }) + '@==##::::##==@'
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
      meta: m.meta,
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

/**
 * 会话是否没有任何对话消息。复用/短路「新对话」标题的会话前必须校验——标题只是占位约定，
 * 历史竞态可能留下"有消息但标题仍是新对话"的脏会话，只认标题会把用户跳进旧会话。
 * 判空口径排除 system 角色（initWorkspace 预创建会话时后端自动写入一条 system
 * 初始化消息，展示层同样过滤它——按"消息数为 0"判会让预创建空会话永远判非空）。
 * 查询失败按非空处理：宁可多建一个新会话，不可误入旧会话。
 */
const isSessionEmpty = async (sessionId: string): Promise<boolean> => {
  try {
    const res = await chatSessionApi.getCurrentMessagesPaged(sessionId, { size: 5 })
    return (res.data?.data?.messages ?? []).every((m) => m.role === 'system')
  } catch {
    return false
  }
}

// 新会话（重入锁：空会话校验/创建均为异步 HTTP，连点会并发多次 createSession
// 冒出一堆空会话——进行中直接忽略后续点击，配合下方复用逻辑保证至多一个空「新对话」）
let creatingNewSession = false
const handleNewSession = async () => {
  if (creatingNewSession) return
  creatingNewSession = true
  try {
    // 断开当前 SSE 连接，旧会话继续后台运行
    preserveRunningSession.value = true
    disconnectStream()

    // 开启工作空间的情况特殊处理
    if (hasCodeExecutionConfig.value) {
      // 当前已在真·空白新对话（标题占位且确实无消息）→ 无需任何动作；
      // 脏会话（有消息但标题仍是占位）不短路，继续往下走复用/新建
      if (currentSessionTitle.value === '新对话' && currentSessionId.value
          && await isSessionEmpty(currentSessionId.value)) {
        return
      }

      // 复用列表中真·空白的「新对话」会话（避免反复新建空会话与工作空间目录）；
      // 逐个校验为空，跳过脏标题会话
      for (const candidate of otherSessions.value.filter((s) => s.title === '新对话')) {
        if (!(await isSessionEmpty(String(candidate.id)))) {
          continue
        }
        // 开启新对话默认一键授权：预置暂存，交由 currentSessionId watch 的回放分支写入 Redis
        // （此路径 sessionId 非空，不会走 !sid 的默认开分支）
        pendingConfirmMode = 'AUTO_APPROVE'
        await selectSession({
          id: candidate.id,
          title: candidate.title || '新对话',
        } as ChatSessionVO)
        return
      }

      const newSession = await createSession(formatSessionTitle(null), true)
      if (!newSession) {
        return
      }
      // 同上：预创建会话路径的默认一键授权
      pendingConfirmMode = 'AUTO_APPROVE'
      resetSession({
        id: String(newSession.id),
        title:'新对话'
      } as ChatSessionVO)
    } else {
      resetSession(null)
    }
  } finally {
    preserveRunningSession.value = false
    creatingNewSession = false
  }
}

/**
 * HITL 刷新恢复：非运行中的会话可能处于「工具确认暂停态」（刷新/重进后前端内存态已丢），
 * 调 /agui/pending 从后端持久暂停态重建「允许/禁止」确认 UI，使其可续点并正常 resume。
 * 暂停态会话已不在 active-runs（RunTracker 已 markCompleted），故独立于 reconnect 单独恢复。
 * @param sid 会话 ID
 */
const restoreConfirm = async (sid: string) => {
  try {
    const pending = await getPending(sid)
    if (pending.length) restorePending(pending)
  } catch {
    // 忽略：无暂停态或网络错误
  }
}

/**
 * 子智能体 HITL 刷新恢复：查挂起中的子确认（进程内注册表）重建子过程确认 UI。
 * 与主流程 restoreConfirm 独立：子确认挂起时主 run 仍在 active-runs（SubAgentTool 在等），
 * 会话切换走 reconnect 分支，SSE 回放会重放 SUBAGENT_CONFIRM_REQUIRED 事件，
 * 本函数兜住回放缓冲已过期等场景（restoreSubPending 幂等，双到达无害）。
 */
const restoreSubConfirm = async (sid: string) => {
  try {
    const pending = await getSubagentPending(sid)
    if (pending.length) restoreSubPending(pending)
  } catch {
    // 忽略：无挂起或网络错误
  }
}

// 选择会话
const handleSelectSession = async (session: ChatSessionVO) => {
  // 暂存只服务「懒创建新会话 / 新对话复用」路径：切到已有会话时作废，
  // 防止欢迎屏里改了模式但未发消息、切走后被回放误写进目标旧会话
  pendingConfirmMode = null
  pendingThinkingMode = null
  // 切换前断开当前 SSE，但不中断后台 Agent
  preserveRunningSession.value = true
  disconnectStream()
  resetStreamingState()
  await selectSession(session)
  // 如果目标会话在运行中，触发重连
  if (runningSessions.value.has(String(session.id))) {
    // 注意：不要加 await，否则会阻塞会话切换
    reconnectStream(String(session.id))
    // 子确认挂起时主 run 仍在运行中（走本分支），恢复子确认 UI（与 SSE 回放幂等互补）
    void restoreSubConfirm(String(session.id))
  } else {
    // 非运行中：尝试恢复 HITL 确认暂停态（不加 await，避免阻塞会话切换）
    void restoreConfirm(String(session.id))
    void restoreSubConfirm(String(session.id))
  }
  preserveRunningSession.value = false
}

// ========== 会话 URL 同步（/chat/:agentId/:sessionId?） ==========

// 外置对话页（Communication）以 props.chatAgentId 嵌入本组件，其 URL 是
// /communication/:chatKey，嵌入模式下禁止改写路由；
// route.name 守卫防止离开对话页时（params 已清空、组件尚未卸载）watch 误改写目标路由
const isStandaloneChatRoute = computed(() => !props.chatAgentId && route.name === RouteNames.CHAT)

// 状态 -> URL：会话变化（选择/新建/发消息懒创建/删除当前会话）统一同步到地址栏；
// replace 不堆浏览器历史，返回键保持"离开对话页"语义
watch(currentSessionId, (sid) => {
  if (!isStandaloneChatRoute.value) return
  const target = sid ? `/chat/${agentId.value}/${sid}` : `/chat/${agentId.value}`
  if (route.path !== target) router.replace(target)
})

/**
 * URL -> 状态：按地址栏中的会话 ID 恢复会话（刷新/直链进入/浏览器前进后退）。
 * 复用 handleSelectSession 完整进入路径（断流、加载消息、运行中重连、HITL 恢复），
 * 因此必须在 runningSessions 就位（getActiveRuns 返回）后调用，否则不会重连 SSE。
 */
const restoreSessionFromRoute = async () => {
  if (!isStandaloneChatRoute.value) return
  const sid = route.params.sessionId as string | undefined
  if (!sid || sid === currentSessionId.value) return
  try {
    const res = await chatSessionApi.getSessionDetail(sid)
    const session = res.data?.data
    if (session && String(session.agentId) === agentId.value) {
      await handleSelectSession(session)
      return
    }
  } catch {
    // 查询失败与"不存在/不属于当前智能体"同样处理
  }
  message.warning('会话不存在或已删除')
  router.replace(`/chat/${agentId.value}`)
}

// 浏览器前进/后退或手动改地址栏时响应（与 currentSessionId 比较防回环）
watch(() => route.params.sessionId, (sid) => {
  if (!isStandaloneChatRoute.value) return
  if (!sid) {
    // 退到无会话 URL（含切换智能体）：有会话则回欢迎页
    if (currentSessionId.value) resetSession(null)
    return
  }
  void restoreSessionFromRoute()
})

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

// HITL §6.5：value = { toolUseId, name, approved }，记录该工具决策（全部决策完内部自动调 resume 续跑）
const handelToolContent = (value: any) => {
  decideConfirm(value.toolUseId, value.approved)
}

// 子智能体 HITL：value = { subToolUseId, approved }，记录子工具决策
// （批次决策齐 useChatStream 内部自动调 /agui/subagent/resume 唤醒子智能体）
const handleSubConfirm = (value: { subToolUseId: string; approved: boolean }) => {
  decideSubConfirm(value.subToolUseId, value.approved)
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
/**
 * 点击欢迎页常用问题卡片：填入输入框后直接发送，与手动输入同链路
 */
const handleQuickQuestion = (question: string) => {
  if (isRunning.value) return
  inputText.value = question
  handleSend()
}

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
  // 标题仍是占位「新对话」且本次有文字 → 用首条消息刷新标题。按占位判断而非消息数
  // （旧条件 messagesList.length<=1 在双发竞态下会漏更，留下"有消息但标题还是新对话"
  // 的脏会话，被 handleNewSession 的复用逻辑误命中跳进旧会话），幂等且与时序无关，
  // 保证不变量：有消息的会话标题必不是「新对话」
  if (currentSessionTitle.value === '新对话' && text) {
    const title = formatSessionTitle(text)
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

// 初始化：获取活跃运行列表后按 URL 恢复会话（运行中重连/HITL 恢复由 handleSelectSession 承担）
onMounted(async () => {
  loadSessions()
  try {
    const activeIds = await getActiveRuns()
    runningSessions.value = new Set(activeIds)
    // 必须在 runningSessions 就位后恢复，否则恢复的运行中会话不会重连 SSE
    await restoreSessionFromRoute()
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
  <div class="chat-page">
    <ChatSidebar
      :collapsed="sidebarCollapsed"
      :agent-name="agentDetail?.name"
      :agent-avatar="agentAvatar"
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
      :title="headerTitle"
      :message-size="messagesList.length"
      :welcome-headline="`来和 ${agentDetail?.name || '智能体'} 聊聊吧`"
      :welcome-desc="agentDetail?.description || '有什么想说的，直接发给我就好～'"
      :common-questions="agentDetail?.commonQuestions"
      :common-questions-pinned="agentDetail?.commonQuestionsPinned !== false"
      :common-questions-collapsed="commonQuestionsCollapsed"
      :agent-avatar="agentAvatar"
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
      :confirm-mode="confirmMode"
      :thinking-supported="thinkingSupported"
      :thinking-active="thinkingActive"
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
      @confirm-mode="handleConfirmModeChange"
      @thinking="handleThinkingChange"
      @toolContent="handelToolContent"
      @sub-confirm="handleSubConfirm"
      @send="handleSend"
      @quick-question="handleQuickQuestion"
      @toggle-questions-collapsed="handleToggleQuestionsCollapsed"
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

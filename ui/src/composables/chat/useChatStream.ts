import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useAgentClient } from '@/composables/useAgentClient'
import { usePlanTracking } from '@/composables/chat/usePlanTracking'
import { buildToolCallsContent, localNowDateTime } from '@/utils/chat/format'
import type {ChatMessageVO, RawEvent} from '@/types'
import { useAccountStore } from '@/stores'
import { stopRun } from '@/api/agui'

let lastIdBig = BigInt(Date.now()) << 12n;
function nextIdBig() {
  return String(lastIdBig++);
}

export function useChatStream(
  agentId: import('vue').Ref<string>,
  agentDetail: import('vue').Ref<any>,
  currentSessionId: import('vue').Ref<string | null>,
  fileIds?: import('vue').Ref<string[]>,
  memoryActive?: import('vue').Ref<boolean>,
  planActive?: import('vue').Ref<boolean>,
  toolProcessActive?: import('vue').Ref<boolean>,
  onMessageSaved?: (chatMsg: ChatMessageVO) => void,
  onRunMeta?: (meta: Record<string, unknown>) => void) {

  const { userInfo } = useAccountStore()

  // 计划追踪
  const {
    currentPlan,
    hasPlan,
    onToolStart: onPlanToolStart,
    onToolArgs: onPlanToolArgs,
    onToolResult: onPlanToolResult,
    resetPlan
  } = usePlanTracking()

  const getForwardedProps = () => ({
    agentId: agentId.value,
    agentCode: agentDetail.value?.agentCode,
    fileIds: fileIds?.value ?? [],
    memoryActive: memoryActive?.value ?? false,
    planActive: planActive?.value ?? false,
    toolProcessActive: toolProcessActive?.value ?? false,
    userInfo: userInfo
  })

  // 流式内容
  const agentHasResult = ref(true)
  const streamingMessageId = ref<string | null>(null)
  const streamingRole = ref<'user' | 'assistant' | 'system' | 'tool' | 'thinking'>('system')
  const streamingContent = ref('')

  // 工具调用进度（subSteps 为子智能体实时过程步骤，SUBAGENT_STEP 事件按 parentToolCallId 追加）
  const toolCallsInProgress = ref<
    Array<{ id: string; name: string; args: string; result?: string; startTime: number; elapsed?: number, needConfirm?: boolean, subSteps?: Array<Record<string, unknown>> }>
  >([])

  // HITL §6.5：逐工具确认决策（toolUseId → 状态），所有项决策完即调 /agui/resume
  const pendingConfirms = ref<Record<string, 'pending' | 'approved' | 'rejected'>>({})

  /**
   * HITL：根据待确认列表重建确认 UI（标记/新建工具项 + 建立逐工具决策态）。两条来源共用：
   * - 实时 TOOL_CONFIRM_REQUIRED 事件：工具项已由 ToolCallStart 建立，只需标记 needConfirm；
   * - 刷新/重进会话（GET /agui/pending 恢复）：toolCallsInProgress 已被清空，须按 input 新建工具项，
   *   否则没有任何工具项承载「允许/禁止」按钮，暂停态卡死无法续点。
   * @param pending 待确认工具 [{toolUseId,name,input}]
   */
  const restorePending = (
    pending: Array<{ toolUseId: string; name: string; input?: Record<string, unknown> }>
  ) => {
    if (!pending || pending.length === 0) return
    const ids = new Set(pending.map(p => p.toolUseId))
    // 已存在的标记 needConfirm
    const arr = toolCallsInProgress.value.map(t => (ids.has(t.id) ? { ...t, needConfirm: true } : t))
    // 缺失的新建（刷新场景）
    const existing = new Set(arr.map(t => t.id))
    pending.forEach(p => {
      if (!existing.has(p.toolUseId)) {
        const args = p.input && Object.keys(p.input).length ? JSON.stringify(p.input) : '{}'
        arr.push({ id: p.toolUseId, name: p.name, args, needConfirm: true, startTime: Date.now() })
      }
    })
    toolCallsInProgress.value = arr
    const next: Record<string, 'pending' | 'approved' | 'rejected'> = { ...pendingConfirms.value }
    pending.forEach(p => { next[p.toolUseId] = 'pending' })
    pendingConfirms.value = next
  }

  // 使用原有的 useAgentClient
  const { messages, isRunning, isReplaying, run, abort, disconnect, reconnect, resume, addUserMessage, client } = useAgentClient({
    handlers: {
      onRunStarted: () => {
        toolCallsInProgress.value = []
        streamingContent.value = ''
        streamingMessageId.value = null
      },
      onTextMessageStart: (e) => {
        streamingRole.value = 'assistant'
        streamingContent.value = ''
        streamingMessageId.value = e.messageId
      },
      onTextMessageContent: (_e, currentText) => {
        agentHasResult.value = true
        streamingContent.value = currentText
      },
      onTextMessageEnd: (_e, finalText) => {
        const sid = currentSessionId.value
        if (sid && finalText) {
          // 纯文本保存，不再与推理打包，通过队列保证写入顺序
          onMessageSaved?.({
            id: streamingMessageId.value,
            sessionId: sid,
            role: streamingRole.value,  // 这里必须使用 streamingRole.value，不能写死 assistant
            content: finalText,
            parentId: '',
            path: '',
            depth: 0,
            createdAt: localNowDateTime()
          } as ChatMessageVO)
        }
        // 无论是否回放，都清除流式状态
        streamingMessageId.value = null
        streamingContent.value = ''
        streamingRole.value = 'system'
      },
      onReasoningMessageStart: (e) => {
        streamingRole.value = 'thinking'
        streamingContent.value = ''
        streamingMessageId.value = e.messageId
      },
      onReasoningMessageContent: (_e, currentText) => {
        streamingContent.value = currentText
      },
      onReasoningMessageEnd: () => {
        const sid = currentSessionId.value
        if (sid && streamingContent.value) {
          // 推理结束时立即保存为独立消息，通过队列保证写入顺序
          onMessageSaved?.({
            id: streamingMessageId.value,
            sessionId: sid,
            role: streamingRole.value, // 这里必须使用 streamingRole.value，不能写死 thinking
            content: streamingContent.value,
            parentId: '',
            path: '',
            depth: 0,
            createdAt: localNowDateTime()
          } as ChatMessageVO)
          // 保存完成后清除推理状态，利用 displayMessages 去重避免闪烁
          streamingMessageId.value = null
          streamingContent.value = ''
          streamingRole.value = 'system'
        }
      },
      onToolCallStart: (e) => {
        // 计划追踪：记录工具调用名称
        onPlanToolStart(e.toolCallId, e.toolCallName)

        agentHasResult.value = true
        toolCallsInProgress.value = [
          ...toolCallsInProgress.value,
          { id: e.toolCallId, name: e.toolCallName, args: '', startTime: Date.now() }
        ]

        const sid = currentSessionId.value
        if (sid && streamingContent.value) {
          // 推理结束时保存为独立消息，通过队列保证写入顺序
          onMessageSaved?.({
            id: streamingMessageId.value,
            sessionId: sid,
            role: streamingRole.value,
            content: streamingContent.value,
            parentId: '',
            path: '',
            depth: 0,
            createdAt: localNowDateTime()
          } as ChatMessageVO)
          // 保存完成后清除推理状态，利用 displayMessages 去重避免闪烁
          streamingMessageId.value = null
          streamingContent.value = ''
          streamingRole.value = 'system'
        }
      },
      onToolCallArgs: (_e, partialArgs) => {
        // 计划追踪：累积工具参数
        onPlanToolArgs(_e.toolCallId, partialArgs)

        // 按 toolCallId 定位目标项（模型并行发起多个工具时，最后一项不一定是本事件的归属）
        const arr = [...toolCallsInProgress.value]
        const target = arr.find(t => t.id === _e.toolCallId)
        if (target) target.args = partialArgs
        toolCallsInProgress.value = arr
      },
      onToolCallResult: (e) => {
        // 计划追踪：处理工具结果
        onPlanToolResult(e.toolCallId)

        try {
          // 判断是否开启了显示工具调用
          if (!(toolProcessActive?.value ?? true)) {
            return
          }
          // 只结算本次 toolCallId 对应的调用：并行场景下数组里可能还有其他进行中的工具，
          // 不能像旧逻辑那样把整个数组序列化/清空（会丢掉后续工具的保存与卡片）
          const completed = toolCallsInProgress.value.find(t => t.id === e.toolCallId)
          if (!completed) {
            return
          }
          const finished = { ...completed, result: e.content, elapsed: Date.now() - completed.startTime }

          // 保存工具调用消息，通过队列保证写入顺序（一次调用一条 tool 消息，与后端历史格式一致）
          const sid = currentSessionId.value
          if (sid) {
            const contentToSave = buildToolCallsContent([finished])
            if (contentToSave) {
              onMessageSaved?.({
                id: nextIdBig(),
                sessionId: sid,
                role: 'tool',
                content: contentToSave,
                parentId: '',
                path: '',
                depth: 0,
                createdAt: localNowDateTime()
              } as ChatMessageVO)
            }
          }
        } finally {
          // 仅移除本次完成的调用，保留其他进行中的工具卡片；
          // 异常残留项由 onRunStarted 的清空兜底（与原有清理时机一致）
          toolCallsInProgress.value = toolCallsInProgress.value.filter(t => t.id !== e.toolCallId)
        }
      },
      onRunFinished: (_e) => {
        agentHasResult.value = true
        // §6.5：不再全标记 needConfirm（旧 Bug1/MCP 假象根源）；
        // 确认改由 onCustom 的 TOOL_CONFIRM_REQUIRED 事件精确驱动
      },
      onRaw: (event) => {
        const e = event as RawEvent
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const rawEvent: any = e.rawEvent
        if(rawEvent.error) {
          streamingMessageId.value = new Date().getTime() + '' + Math.floor(Math.random() * 90000) + 10000
          streamingContent.value = rawEvent.error
          const sid = currentSessionId.value
          if (sid) {
            onMessageSaved?.({
              id: streamingMessageId.value,
              sessionId: sid,
              role: 'error',
              content: rawEvent.error,
              parentId: '',
              path: '',
              depth: 0,
              createdAt: localNowDateTime()
            } as ChatMessageVO)
            // 保存完成后清除推理状态，利用 displayMessages 去重避免闪烁
            streamingMessageId.value = null
            streamingContent.value = ''
            streamingRole.value = 'system'
          }
        }
     },
      onCustom: (event) => {
        // HITL §6.2/§6.5：收到 TOOL_CONFIRM_REQUIRED 时，精确标记需确认的工具（不再全标记）
        if (event.name === 'TOOL_CONFIRM_REQUIRED') {
          const pending = (((event.value as any)?.pending) ?? []) as Array<{ toolUseId: string; name: string; input?: Record<string, unknown> }>
          restorePending(pending)
        }
        // 子智能体过程步骤增量（载荷契约见后端 AguiCustomEvents.SUBAGENT_STEP）
        else if (event.name === 'SUBAGENT_STEP') {
          handleSubagentStep(event.value as Record<string, unknown>)
        }
        // run 级元数据（RUN_FINISHED 前下发，与落库 meta 同构）：回填最后一条 assistant 消息
        else if (event.name === 'RUN_META') {
          onRunMeta?.(event.value as Record<string, unknown>)
        }
      }
    }
  })

  /**
   * SUBAGENT_STEP 装配：把子智能体的实时步骤追加/合并到对应的进行中工具卡片。
   * 关联优先 parentToolCallId 精确匹配，缺失时按工具名（子智能体 agentCode 小写）降级匹配；
   * 工具步两段式：running 步先出现，完成事件按 subToolUseId 找回原步骤补 result/elapsed
   */
  function handleSubagentStep(value: Record<string, unknown>) {
    const step = value?.step as Record<string, unknown> | undefined
    if (!step) return

    const parentToolCallId = String(value.parentToolCallId ?? '')
    const subagentName = String(value.subagentName ?? '').toLowerCase()
    const subToolUseId = value.subToolUseId != null ? String(value.subToolUseId) : undefined

    const arr = [...toolCallsInProgress.value]
    const target = (parentToolCallId && arr.find(t => t.id === parentToolCallId))
      || arr.find(t => t.name.toLowerCase() === subagentName && t.result == null)
    if (!target) return

    const subSteps = [...(target.subSteps ?? [])]
    // 倒序找同类型的流式进行中步骤（delta 追加与轮末定稿共用）
    const lastStreamingIdx = (type: unknown): number => {
      for (let i = subSteps.length - 1; i >= 0; i--) {
        const s = subSteps[i]!
        if (s.type === type && s.streaming) return i
      }
      return -1
    }
    if (step.delta != null && (step.type === 'thinking' || step.type === 'text')) {
      // 流式增量：追加到同类型的进行中步骤，没有则新建（子智能体思考/回复逐字出现）
      const idx = lastStreamingIdx(step.type)
      if (idx >= 0) {
        subSteps[idx] = { ...subSteps[idx], content: String(subSteps[idx]!.content ?? '') + String(step.delta) }
      } else {
        subSteps.push({ type: step.type, content: String(step.delta), streaming: true })
      }
    } else if ((step.type === 'thinking' || step.type === 'text') && step.content != null) {
      // 轮末完整步：定稿替换对应的流式步（内容以完整版为准，与落库一致）；无流式步则直接追加
      const idx = lastStreamingIdx(step.type)
      if (idx >= 0) {
        subSteps[idx] = { ...step }
      } else {
        subSteps.push({ ...step })
      }
    } else if (step.type === 'tool' && !step.running && subToolUseId) {
      // 工具完成事件：按 subToolUseId 找回 running 步合并；找不到则降级为独立步
      const idx = subSteps.findIndex(s => s.subToolUseId === subToolUseId && s.running)
      if (idx >= 0) {
        subSteps[idx] = { ...subSteps[idx], ...step, running: undefined, startTime: undefined }
      } else {
        subSteps.push({ ...step, subToolUseId })
      }
    } else if (step.type === 'tool' && step.running) {
      // 工具发起：记录前端到达时刻，供"执行中"耗时递增显示
      subSteps.push({ ...step, subToolUseId, startTime: Date.now() })
    } else {
      subSteps.push(subToolUseId ? { ...step, subToolUseId } : { ...step })
    }
    target.subSteps = subSteps
    toolCallsInProgress.value = arr
  }

  /**
   * HITL §6.5：记录单个工具的确认决策（替代旧的「前端代执行/塞文本 + run 重开一轮」）。
   * 所有待确认工具都决策后，调用 /agui/resume 由后端从暂停点续跑。
   * @param toolUseId 工具调用 id（= TOOL_CONFIRM_REQUIRED 的 toolUseId）
   * @param approved true=允许，false=拒绝
   */
  const decideConfirm = (toolUseId: string, approved: boolean) => {
    if (pendingConfirms.value[toolUseId] === undefined) return
    pendingConfirms.value = {
      ...pendingConfirms.value,
      [toolUseId]: approved ? 'approved' : 'rejected'
    }
    // 该工具按钮收起（已决策）
    toolCallsInProgress.value = toolCallsInProgress.value.map(t =>
      t.id === toolUseId ? { ...t, needConfirm: false } : t
    )
    // 所有待确认工具都已决策 → 提交 resume
    const states = Object.values(pendingConfirms.value)
    if (states.length > 0 && states.every(s => s !== 'pending')) {
      void submitResume()
    }
  }

  /** 汇总逐工具决策并调用后端 resume，续接 SSE 流。 */
  const submitResume = async () => {
    const sid = currentSessionId.value
    if (!sid) return
    const decisions = Object.entries(pendingConfirms.value).map(([toolUseId, s]) => {
      const t = toolCallsInProgress.value.find(x => x.id === toolUseId)
      return { toolUseId, name: t?.name ?? '', approved: s === 'approved' }
    })
    pendingConfirms.value = {}
    agentHasResult.value = false
    await resume(sid, decisions, memoryActive?.value ?? false)
  }

  // 中止运行
  const abortRun = async  () => {
    // 先调用后端 stop API 强制中断
    const sid = currentSessionId.value
    if (sid) {
      try { await stopRun(sid) } catch { /* 忽略 stop API 错误 */ }
    }
    await abort()
    agentHasResult.value = true

    // 重置计划状态
    resetPlan()

    if (sid) {
      // 保存工具调用消息，通过队列保证写入顺序
      if (toolCallsInProgress.value.length > 0) {
        const contentToSave = buildToolCallsContent(toolCallsInProgress.value)
        if (contentToSave) {
          onMessageSaved?.({
            id: nextIdBig(),
            sessionId: sid,
            role: 'tool',
            content: contentToSave,
            parentId: '',
            path: '',
            depth: 0,
            createdAt: localNowDateTime()
          } as ChatMessageVO)
        }
      }
      // 保存AI回复消息
      else {
        if (streamingContent.value) {
          onMessageSaved?.({
            id: streamingMessageId.value,
            sessionId: sid,
            role: streamingRole.value,
            content: streamingContent.value,
            parentId: '',
            path: '',
            depth: 0,
            createdAt: localNowDateTime()
          } as ChatMessageVO)
        }
      }

      toolCallsInProgress.value = []
      streamingMessageId.value = null
      streamingContent.value = ''
      streamingRole.value = 'system'
      isRunning.value = false

    }

  }

  // 发送消息（可选传入 fileIds 覆盖，用于发送时已清空输入框的场景）
  const sendMessage = async (
    inputText: string,
    messagesList: ChatMessageVO[],
    overrideFileIds?: string[]
  ) => {
    const effectiveFileIds = overrideFileIds ?? fileIds?.value ?? []
    if (!agentId.value) return
    if (!inputText.trim() && !effectiveFileIds.length) return
    if (isRunning.value) return
    if (!agentDetail.value?.agentCode) {
      message.error('智能体信息未加载完成，请稍后再试')
      return
    }

    // 构建 client 需要的消息格式
    client.messages = messagesList
      .filter((m) => !['system', 'tool'].includes(m.role))
      .map((m) => ({
        id: String(m.id),
        role: m.role as any,
        content: (m.content || '') as string
      }))

    const forwardedProps = getForwardedProps()
    if (overrideFileIds !== undefined) {
      forwardedProps.fileIds = overrideFileIds
    }

    agentHasResult.value = false
    await run({
      threadId: currentSessionId.value || undefined,
      runId: `run_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`,
      forwardedProps
    })
  }

  /**
   * 重置所有流式状态，用于会话切换时清理旧 session 残留。
   */
  function resetStreamingState() {
    streamingMessageId.value = null
    streamingContent.value = ''
    streamingRole.value = 'system'
    toolCallsInProgress.value = []
    agentHasResult.value = true
    currentPlan.value = null
  }

  return {
    agentHasResult,
    streamingContent,
    streamingMessageId,
    streamingRole,
    toolCallsInProgress,
    isRunning,
    isReplaying,
    currentPlan,
    hasPlan,
    abortRun,
    sendMessage,
    decideConfirm,
    pendingConfirms,
    restorePending,
    reconnect,
    disconnect,
    resetStreamingState,
    client, // 如果需要暴露
  }
}

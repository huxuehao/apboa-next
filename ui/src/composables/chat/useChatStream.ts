import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useAgentClient } from '@/composables/useAgentClient'
import { usePlanTracking } from '@/composables/chat/usePlanTracking'
import { buildToolCallsContent, localNowDateTime } from '@/utils/chat/format'
import type {ChatMessageVO, ConfirmFieldMeta, RawEvent} from '@/types'
import { stopRun, subagentResume, type SubPendingInfo } from '@/api/agui'

let lastIdBig = BigInt(Date.now()) << 12n;
function nextIdBig() {
  return String(lastIdBig++);
}

/**
 * 子确认拒绝的本地占位结果（与后端 AguiRequestProcessor.REJECT_RESULT_TEXT 保持一致，
 * 权威版由后端直发拒绝完成步配对覆盖——两者同文时覆盖无视觉变化，事件丢失时兜底同构）
 */
const SUB_REJECT_RESULT_TEXT =
  'Error: 用户拒绝授权调用该工具，本轮对话中该工具不可用。请勿重试该工具，' +
  '更不得自行编造、虚构或凭常识臆测该工具本应返回的结果数据；' +
  '必须如实告知用户：因未获授权调用该工具，无法获取相关信息。'

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

  // 计划追踪
  const {
    currentPlan,
    hasPlan,
    onToolStart: onPlanToolStart,
    onToolArgs: onPlanToolArgs,
    onToolResult: onPlanToolResult,
    resetPlan
  } = usePlanTracking()

  // userInfo 不再随 forwardedProps 上送：后端已改为服务端认证盖章
  // （AgentContext 只认 AuthInterceptor 的 UserDetail，自报值一律不采信）
  const getForwardedProps = () => ({
    agentId: agentId.value,
    agentCode: agentDetail.value?.agentCode,
    fileIds: fileIds?.value ?? [],
    memoryActive: memoryActive?.value ?? false,
    planActive: planActive?.value ?? false,
    toolProcessActive: toolProcessActive?.value ?? false
  })

  // 流式内容
  const agentHasResult = ref(true)
  const streamingMessageId = ref<string | null>(null)
  const streamingRole = ref<'user' | 'assistant' | 'system' | 'tool' | 'thinking'>('system')
  const streamingContent = ref('')

  // 工具调用进度（subSteps 为子智能体实时过程步骤，SUBAGENT_STEP 事件按 parentToolCallId 追加；
  // finished 由 TOOL_FINISHED 事件即时置位——工具真正跑完的瞬间翻转完成态，结果详情仍等批量 Result）
  const toolCallsInProgress = ref<
    Array<{ id: string; name: string; args: string; result?: string; startTime: number; elapsed?: number, finished?: boolean, needConfirm?: boolean, confirmFields?: ConfirmFieldMeta[], confirmSummary?: string, subSteps?: Array<Record<string, unknown>> }>
  >([])

  // HITL §6.5：逐工具确认决策（toolUseId → 状态），所有项决策完即调 /agui/resume
  const pendingConfirms = ref<Record<string, 'pending' | 'approved' | 'rejected'>>({})

  // HITL 改参：确认 UI 中用户修改后的参数暂存（toolUseId / subToolUseId → 参数），
  // 决策时写入、随 resume 提交后清理；未修改的工具无条目（resume 沿用模型原始参数）
  let editedInputs: Record<string, Record<string, unknown>> = {}
  let editedSubInputs: Record<string, Record<string, unknown>> = {}

  // HITL 决策后业务摘要（toolUseId → 定制确认卡提供的一行摘要）：必须存在组件外——
  // resume 续跑的 onRunStarted 会清空重建工具卡，组件本地状态活不过重建；
  // onToolCallStart 重建卡片时按 id 回填
  const confirmSummaries: Record<string, string> = {}

  // HITL 决策态（toolUseId → approved/rejected）：工具完成落地本地 tool 消息时随
  // buildToolCallsContent 写入 confirmState，与后端落库格式同构（历史渲染确认徽标+只读回显）
  const confirmStates: Record<string, 'approved' | 'rejected'> = {}

  // 工具权威耗时表（TOOL_ELAPSED 事件先于结果事件到达；非响应式，消费即删）。
  // 全链路唯一计时者是后端 ChatLogHook——前端不掐表，表无值就不显示耗时（宁缺毋错），
  // 消除实时显示与落库值两次测量的误差
  let authoritativeElapsed: Record<string, number> = {}

  // 子智能体 HITL：挂起中确认批次（subSessionId → 批次）。子智能体一次暂停冒泡一批
  // 待确认工具，批内决策齐即调 /agui/subagent/resume 唤醒（与主流程 pendingConfirms 平行）
  const pendingSubConfirms = ref<Record<string, {
    parentToolCallId: string
    subagentName: string
    /** subToolUseId → 决策态 */
    decisions: Record<string, 'pending' | 'approved' | 'rejected'>
    /** subToolUseId → 工具名（决策回传原始 name） */
    names: Record<string, string>
  }>>({})

  /**
   * HITL：根据待确认列表重建确认 UI（标记/新建工具项 + 建立逐工具决策态）。两条来源共用：
   * - 实时 TOOL_CONFIRM_REQUIRED 事件：工具项已由 ToolCallStart 建立，只需标记 needConfirm；
   * - 刷新/重进会话（GET /agui/pending 恢复）：toolCallsInProgress 已被清空，须按 input 新建工具项，
   *   否则没有任何工具项承载「允许/禁止」按钮，暂停态卡死无法续点。
   * @param pending 待确认工具 [{toolUseId,name,input}]
   */
  const restorePending = (
    pending: Array<{ toolUseId: string; name: string; input?: Record<string, unknown>; fields?: ConfirmFieldMeta[] }>
  ) => {
    if (!pending || pending.length === 0) return
    const byId = new Map(pending.map(p => [p.toolUseId, p]))
    // 已存在的标记 needConfirm（fields 一并挂上，驱动确认表单渲染）
    const arr = toolCallsInProgress.value.map(t => {
      const p = byId.get(t.id)
      return p ? { ...t, needConfirm: true, confirmFields: p.fields } : t
    })
    // 缺失的新建（刷新场景）
    const existing = new Set(arr.map(t => t.id))
    pending.forEach(p => {
      if (!existing.has(p.toolUseId)) {
        const args = p.input && Object.keys(p.input).length ? JSON.stringify(p.input) : '{}'
        arr.push({ id: p.toolUseId, name: p.name, args, needConfirm: true, confirmFields: p.fields, startTime: Date.now() })
      }
    })
    toolCallsInProgress.value = arr
    const next: Record<string, 'pending' | 'approved' | 'rejected'> = { ...pendingConfirms.value }
    pending.forEach(p => { next[p.toolUseId] = 'pending' })
    pendingConfirms.value = next
  }

  /**
   * 子智能体 HITL：把确认请求装配到对应工具卡片的子过程步骤上（标注/新建工具步 + 建批次决策态）。
   * 两条来源共用（载荷同构）：
   * - 实时 SUBAGENT_CONFIRM_REQUIRED 事件：工具步已由 SUBAGENT_STEP 建立（running 转圈中），
   *   按 subToolUseId 找到并标 needConfirm（清 running——工具没在跑，是在等人，与主卡片一致）；
   * - 刷新/重进会话（GET /agui/subagent/pending 恢复）：卡片与步骤均已丢失，须新建承载按钮。
   */
  const restoreSubPending = (items: SubPendingInfo[]) => {
    if (!items || items.length === 0) return
    const arr = [...toolCallsInProgress.value]
    const nextBatches = { ...pendingSubConfirms.value }
    items.forEach(info => {
      if (!info?.subSessionId || !info.pending?.length) return
      // 找/建主卡片（刷新场景卡片已丢，按 parentToolCallId 重建；name 与子智能体工具名一致）
      let target = info.parentToolCallId ? arr.find(t => t.id === info.parentToolCallId) : undefined
      if (!target) {
        target = {
          id: info.parentToolCallId || `sub_${info.subSessionId}`,
          name: String(info.subagentName || '').toLowerCase(),
          args: '{}',
          startTime: Date.now()
        }
        arr.push(target)
      }
      const subSteps = [...(target.subSteps ?? [])]
      const decisions: Record<string, 'pending' | 'approved' | 'rejected'> = {}
      const names: Record<string, string> = {}
      info.pending.forEach(p => {
        decisions[p.toolUseId] = 'pending'
        names[p.toolUseId] = p.name
        const idx = subSteps.findIndex(s => s.subToolUseId === p.toolUseId)
        if (idx >= 0) {
          subSteps[idx] = { ...subSteps[idx], needConfirm: true, fields: p.fields, running: undefined, startTime: undefined }
        } else {
          const args = p.input && Object.keys(p.input).length ? JSON.stringify(p.input) : '{}'
          subSteps.push({ type: 'tool', name: p.name, args, needConfirm: true, fields: p.fields, subToolUseId: p.toolUseId })
        }
      })
      target.subSteps = subSteps
      nextBatches[info.subSessionId] = {
        parentToolCallId: target.id,
        subagentName: String(info.subagentName ?? ''),
        decisions,
        names
      }
    })
    toolCallsInProgress.value = arr
    pendingSubConfirms.value = nextBatches
  }

  // 使用原有的 useAgentClient
  const { messages, isRunning, isReplaying, run, abort, disconnect, reconnect, resume, addUserMessage, client } = useAgentClient({
    handlers: {
      onRunStarted: () => {
        toolCallsInProgress.value = []
        streamingContent.value = ''
        streamingMessageId.value = null
        authoritativeElapsed = {}
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
        // reconnect 回放的事件不再 push 本地：对应内容已由 ChatLogHook 落库、loadMessages
        // 已加载（id 是后端雪花 id 与事件 messageId 不同，无法按 id 去重），再 push 即重复
        // 两段。client.isReplaying 由 REPLAY_CAUGHT_UP 即时翻转，追平后的实时事件照常保存
        if (sid && finalText && !client.isReplaying) {
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
          // 回放事件不重复入列（同 onTextMessageEnd，防思考重复两段），流式状态照常清理
          if (!client.isReplaying) {
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
          }
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
          { id: e.toolCallId, name: e.toolCallName, args: '', startTime: Date.now(), confirmSummary: confirmSummaries[e.toolCallId] }
        ]

        const sid = currentSessionId.value
        if (sid && streamingContent.value) {
          // 回放事件不重复入列（防思考重复两段），流式状态照常清理
          if (!client.isReplaying) {
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
          }
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
          // 耗时只用后端权威值（TOOL_ELAPSED 已先行到达），不掐表；无值则不显示
          const authElapsed = authoritativeElapsed[e.toolCallId]
          delete authoritativeElapsed[e.toolCallId]
          // 确认态三源合一：人工决策（decideConfirm）> TOOL_FINISHED 下发（一键授权）>
          // 拒绝文案前缀兜底（拒绝授权模式就地全拒，无人工决策也无完成事件）
          const confirmState = confirmStates[e.toolCallId]
            ?? (typeof e.content === 'string' && e.content.startsWith('Error: 用户拒绝授权调用该工具') ? 'rejected' as const : undefined)
          const finished = { ...completed, result: e.content, elapsed: authElapsed, confirmState }

          // 保存工具调用消息，通过队列保证写入顺序（一次调用一条 tool 消息，与后端历史格式一致）；
          // 回放事件不入列——库版已由 loadMessages 加载，回放版 elapsed 是事件到达间隔而非真实
          // 耗时，重复入列即出现「同一工具卡两份、耗时各异」
          const sid = currentSessionId.value
          if (sid && !client.isReplaying) {
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
          const pending = (((event.value as any)?.pending) ?? []) as Array<{ toolUseId: string; name: string; input?: Record<string, unknown>; fields?: ConfirmFieldMeta[] }>
          restorePending(pending)
        }
        // 子智能体过程步骤增量（载荷契约见后端 AguiCustomEvents.SUBAGENT_STEP）
        else if (event.name === 'SUBAGENT_STEP') {
          handleSubagentStep(event.value as Record<string, unknown>)
        }
        // 子智能体 HITL 确认请求（子智能体内需确认工具挂起等待，契约见 SUBAGENT_CONFIRM_REQUIRED）
        else if (event.name === 'SUBAGENT_CONFIRM_REQUIRED') {
          restoreSubPending([event.value as unknown as SubPendingInfo])
        }
        // 单工具完成即时通知（工具真正跑完的瞬间到达，先于批量 Result——后者受
        // 批级 collectList 屏障要等整批完成）：翻转完成态+定格真实耗时；
        // 串行执行下本工具完成=下一个未完成工具此刻开始执行，重置其掐表起点
        // （其 startTime 原是 ToolCallStart 到达时刻，含排队等待，直接用会虚大）
        else if (event.name === 'TOOL_FINISHED') {
          const v = event.value as { toolUseId?: string; elapsed?: number; confirmState?: 'approved' | 'rejected' }
          if (v?.toolUseId != null) {
            // 确认态随完成事件下发（一键授权等自动模式无人工决策，本地无从判定），
            // 先于批量 Result 到达，落地时经 confirmStates 写入 tool 消息
            if (v.confirmState) {
              confirmStates[String(v.toolUseId)] = v.confirmState
            }
            const arr = [...toolCallsInProgress.value]
            const idx = arr.findIndex(t => t.id === String(v.toolUseId))
            if (idx >= 0) {
              const cur = arr[idx]!
              arr[idx] = { ...cur, finished: true, elapsed: typeof v.elapsed === 'number' ? v.elapsed : cur.elapsed }
              const next = arr.find(t => !t.finished && t.result == null)
              if (next) {
                next.startTime = Date.now()
              }
              toolCallsInProgress.value = arr
            }
          }
        }
        // 工具权威耗时（后端唯一计时者的落库同源值，先于对应结果事件到达）
        else if (event.name === 'TOOL_ELAPSED') {
          const v = event.value as { toolUseId?: string; elapsed?: number }
          if (v?.toolUseId != null && typeof v.elapsed === 'number') {
            authoritativeElapsed[String(v.toolUseId)] = v.elapsed
          }
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
      // 工具完成事件：按 subToolUseId 找回未落定步骤合并（running=执行中 / decided=已决策
      // 待续跑 / needConfirm=等确认中被后端超时全拒），找不到才降级为独立步
      const idx = subSteps.findIndex(s => s.subToolUseId === subToolUseId && (s.running || s.decided || s.needConfirm))
      if (idx >= 0) {
        // 等确认中直接收到完成结果 = 后端已超时按全拒绝处理：按钮随 needConfirm 清除消失，
        // 同步清掉已失效的批次决策态（防止一键授权联动等再向已死批次提交）
        if (subSteps[idx]!.needConfirm) {
          cleanupExpiredSubConfirm(subToolUseId)
        }
        subSteps[idx] = { ...subSteps[idx], ...step, running: undefined, startTime: undefined, decided: undefined, needConfirm: undefined }
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
   * @param editedInput 用户在确认 UI 中修改后的参数；缺省=未修改（后端沿用模型原始参数）
   */
  const decideConfirm = (toolUseId: string, approved: boolean, editedInput?: Record<string, unknown>, summary?: string) => {
    if (pendingConfirms.value[toolUseId] === undefined) return
    if (approved && editedInput && Object.keys(editedInput).length) {
      editedInputs[toolUseId] = editedInput
      // 卡片参数展示同步为改后值（与后端记忆改写、落库 args 一致）
      toolCallsInProgress.value = toolCallsInProgress.value.map(t =>
        t.id === toolUseId ? { ...t, args: JSON.stringify(editedInput) } : t
      )
    }
    if (approved && summary) {
      confirmSummaries[toolUseId] = summary
      toolCallsInProgress.value = toolCallsInProgress.value.map(t =>
        t.id === toolUseId ? { ...t, confirmSummary: summary } : t
      )
    }
    confirmStates[toolUseId] = approved ? 'approved' : 'rejected'
    pendingConfirms.value = {
      ...pendingConfirms.value,
      [toolUseId]: approved ? 'approved' : 'rejected'
    }
    // 该工具按钮收起（已决策）。拒绝的工具不本地落定：resume 流头会下发
    // 拒绝结果事件（含 TOOL_ELAPSED 权威耗时），收尾统一走 onToolCallResult
    // ——实时显示与落库同一次测量，前端不掐表
    toolCallsInProgress.value = toolCallsInProgress.value.map(t =>
      t.id === toolUseId ? { ...t, needConfirm: false } : t
    )
    // 所有待确认工具都已决策 → 提交 resume
    const states = Object.values(pendingConfirms.value)
    if (states.length > 0 && states.every(s => s !== 'pending')) {
      void submitResume()
    }
  }

  /** 汇总逐工具决策（含用户修改后的参数）并调用后端 resume，续接 SSE 流。 */
  const submitResume = async () => {
    const sid = currentSessionId.value
    if (!sid) return
    const decisions = Object.entries(pendingConfirms.value).map(([toolUseId, s]) => {
      const t = toolCallsInProgress.value.find(x => x.id === toolUseId)
      const approved = s === 'approved'
      return { toolUseId, name: t?.name ?? '', approved, input: approved ? editedInputs[toolUseId] : undefined }
    })
    pendingConfirms.value = {}
    editedInputs = {}
    agentHasResult.value = false
    await resume(sid, decisions, memoryActive?.value ?? false)
  }

  /**
   * 清理已失效的子确认决策态：等确认中的工具直接收到完成结果（后端超时全拒/外部决策），
   * 该工具的决策项已无意义，从批次中移除；批次清空则整体删除。
   */
  const cleanupExpiredSubConfirm = (subToolUseId: string) => {
    const entry = Object.entries(pendingSubConfirms.value)
      .find(([, b]) => b.decisions[subToolUseId] !== undefined)
    if (!entry) return
    const [subSessionId, batch] = entry
    const decisions = { ...batch.decisions }
    delete decisions[subToolUseId]
    const next = { ...pendingSubConfirms.value }
    if (Object.keys(decisions).length === 0) {
      delete next[subSessionId]
    } else {
      next[subSessionId] = { ...batch, decisions }
    }
    pendingSubConfirms.value = next
  }

  /**
   * 子智能体 HITL：记录单个子工具的确认决策（subToolUseId 全局唯一，直接反查所属批次）。
   * 该批次全部决策后自动提交 /agui/subagent/resume 唤醒子智能体续跑（续跑步骤沿原 SSE 流流入）。
   * @param editedInput 用户在确认 UI 中修改后的参数；缺省=未修改
   */
  const decideSubConfirm = (subToolUseId: string, approved: boolean, editedInput?: Record<string, unknown>) => {
    const entry = Object.entries(pendingSubConfirms.value)
      .find(([, b]) => b.decisions[subToolUseId] === 'pending')
    if (!entry) return
    const [subSessionId, batch] = entry
    if (approved && editedInput && Object.keys(editedInput).length) {
      editedSubInputs[subToolUseId] = editedInput
    }
    const nextBatch = {
      ...batch,
      decisions: { ...batch.decisions, [subToolUseId]: (approved ? 'approved' : 'rejected') as 'approved' | 'rejected' }
    }
    pendingSubConfirms.value = { ...pendingSubConfirms.value, [subSessionId]: nextBatch }
    // 该步按钮收起并标 decided（续跑完成事件按 subToolUseId+decided 配对合并）：
    // 允许 → 恢复 running 转圈等真实执行结果；拒绝 → 本地预落定拒绝结果兜底
    // （后端会直发拒绝完成步 SUBAGENT_STEP 配对覆盖，文案/elapsed 同源无视觉变化）
    const arr = [...toolCallsInProgress.value]
    const card = arr.find(t => t.id === nextBatch.parentToolCallId)
    if (card?.subSteps) {
      card.subSteps = card.subSteps.map(s => {
        if (s.subToolUseId !== subToolUseId) return s
        return approved
          ? {
              ...s,
              needConfirm: undefined,
              decided: true,
              running: true,
              startTime: Date.now(),
              // 改参时步骤参数展示同步为改后值（与子 agent 记忆改写一致）
              ...(editedInput && Object.keys(editedInput).length ? { args: JSON.stringify(editedInput) } : {})
            }
          : {
              ...s,
              needConfirm: undefined,
              decided: true,
              running: undefined,
              startTime: undefined,
              result: SUB_REJECT_RESULT_TEXT
              // elapsed 不本地计算：等后端直发拒绝步的落库同源值配对覆盖（宁缺毋错）
            }
      })
      toolCallsInProgress.value = arr
    }
    // 批次决策齐 → 提交
    if (Object.values(nextBatch.decisions).every(s => s !== 'pending')) {
      void submitSubResume(subSessionId)
    }
  }

  /** 汇总某批次的子确认决策（含用户修改后的参数）并调用 /agui/subagent/resume 唤醒挂起的子智能体。 */
  const submitSubResume = async (subSessionId: string) => {
    const batch = pendingSubConfirms.value[subSessionId]
    if (!batch) return
    const decisions = Object.entries(batch.decisions).map(([toolUseId, s]) => {
      const approved = s === 'approved'
      return {
        toolUseId,
        name: batch.names[toolUseId] ?? '',
        approved,
        input: approved ? editedSubInputs[toolUseId] : undefined
      }
    })
    Object.keys(batch.decisions).forEach(id => { delete editedSubInputs[id] })
    const next = { ...pendingSubConfirms.value }
    delete next[subSessionId]
    pendingSubConfirms.value = next
    try {
      const res = await subagentResume(subSessionId, decisions)
      if (!res.resumed) {
        // 挂起已失效（超时全拒绝/已决策/实例重启）：后端已自行收尾，前端提示即可
        message.warning(res.error || '子智能体确认已失效')
      }
    } catch (e) {
      message.error('子智能体确认提交失败')
      console.error('subagentResume failed:', e)
    }
  }

  /** 授权模式联动：把所有挂起中的子确认统一决策（一键授权→全批 true / 拒绝授权→全拒 false） */
  const decideAllSubPending = (approved: boolean) => {
    Object.values(pendingSubConfirms.value).forEach(batch => {
      Object.entries(batch.decisions).forEach(([subToolUseId, state]) => {
        if (state === 'pending') decideSubConfirm(subToolUseId, approved)
      })
    })
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
    // 子确认批次跟随会话内存态清理（重进会话由 GET /agui/subagent/pending 重建）
    pendingSubConfirms.value = {}
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
    decideSubConfirm,
    pendingSubConfirms,
    restoreSubPending,
    decideAllSubPending,
    reconnect,
    disconnect,
    resetStreamingState,
    client, // 如果需要暴露
  }
}

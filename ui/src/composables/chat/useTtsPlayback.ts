/**
 * 语音播报（全局单例，订阅式）：合成全部在服务端会话内完成，
 * 前端只做三件事——经现有消息 WS 订阅/退订某 thread 的音频流、
 * 解二进制帧喂流式播放器、按 start/end/error 事件驱动状态。
 *
 * 通道：TTS_SUBSCRIBE/TTS_UNSUBSCRIBE（client→server JSON）、
 * TTS_STREAM 事件与二进制 PCM 帧（server→client，同一条 WS 混流）。
 * 断线重连后自动重发订阅。
 *
 * @author huxuehao
 */
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import * as ttsApi from '@/api/tts'
import { ttsStreamPlayer } from '@/utils/audio/ttsStreamPlayer'
import { eventBus } from '@/websocket/core/event-bus'
import { TTS_AUDIO_FRAME_EVENT, WS_MESSAGE_TYPES } from '@/websocket/const/websocket'
import { getActiveWsHandler } from '@/websocket/useWebSocket'
import type { EnhancedMessageHandler } from '@/websocket/handlers/enhanced-message-handler'

const AUTO_BROADCAST_KEY = 'chat-tts-auto-broadcast'

/** 自动播报开关（跨会话持久） */
const autoBroadcast = ref(localStorage.getItem(AUTO_BROADCAST_KEY) === '1')

/** 是否有播报活动（收到 start 到缓冲播空之间） */
const speaking = ref(false)

/** 手动朗读中的消息 id（驱动消息操作栏按钮态），自动播报不设置 */
const speakingMessageId = ref<string | null>(null)

const currentAgentId = ref<string>('')
const currentThreadId = ref<string>('')

/** 当前已向服务端登记订阅的 threadId（null=未订阅） */
let subscribedThreadId: string | null = null

/** WS 发送通道：实时从连接模块取（本模块懒加载，不能依赖 CONNECTED 事件时序） */
function wsHandler(): EnhancedMessageHandler | null {
  return getActiveWsHandler()
}

// ==================== 模块级事件接线（应用生命周期内常驻） ====================

eventBus.on('WEBSOCKET:CONNECTED', () => {
  // 断线重连：恢复订阅（服务端旧 session 的登记已随断连清理）
  if (subscribedThreadId) {
    wsHandler()?.sendMessage(WS_MESSAGE_TYPES.TTS_SUBSCRIBE, {
      threadId: subscribedThreadId,
      agentId: currentAgentId.value
    })
  }
})

eventBus.on(WS_MESSAGE_TYPES.TTS_STREAM, (event: {
  event: string
  threadId: string
  sampleRate?: number
  bitsPerSample?: number
  channels?: number
  message?: string
}) => {
  if (!event || event.threadId !== subscribedThreadId) {
    return
  }
  if (event.event === 'start') {
    speaking.value = true
    void ttsStreamPlayer.configure(event.sampleRate || 24000)
  } else if (event.event === 'end') {
    ttsStreamPlayer.markEnd()
  } else if (event.event === 'error') {
    message.error(event.message || '语音播报失败')
    ttsStreamPlayer.stop()
    speaking.value = false
    speakingMessageId.value = null
  }
})

eventBus.on(TTS_AUDIO_FRAME_EVENT, (frame: ArrayBuffer) => {
  const decoded = decodeFrame(frame)
  if (!decoded || decoded.threadId !== subscribedThreadId) {
    return
  }
  speaking.value = true
  ttsStreamPlayer.feed(decoded.pcm)
})

ttsStreamPlayer.onDrained = () => {
  speaking.value = false
  speakingMessageId.value = null
  // 静音（开关关闭）状态下播完当前条：退订，后续新回复不再合成（懒合成原则）
  if (!autoBroadcast.value) {
    unsubscribe()
  }
}

/** 解二进制帧：[2B threadId长度][threadId UTF-8][4B 序号][PCM16] */
function decodeFrame(frame: ArrayBuffer): { threadId: string; seq: number; pcm: ArrayBuffer } | null {
  if (!frame || frame.byteLength < 6) {
    return null
  }
  const view = new DataView(frame)
  const tidLen = view.getUint16(0)
  if (frame.byteLength < 2 + tidLen + 4) {
    return null
  }
  const threadId = new TextDecoder().decode(new Uint8Array(frame, 2, tidLen))
  const seq = view.getInt32(2 + tidLen)
  return { threadId, seq, pcm: frame.slice(2 + tidLen + 4) }
}

// ==================== 订阅管理 ====================

function subscribe(threadId: string, agentId: string) {
  if (subscribedThreadId === threadId) {
    return
  }
  unsubscribe()
  subscribedThreadId = threadId
  wsHandler()?.sendMessage(WS_MESSAGE_TYPES.TTS_SUBSCRIBE, { threadId, agentId })
}

function unsubscribe() {
  if (subscribedThreadId) {
    wsHandler()?.sendMessage(WS_MESSAGE_TYPES.TTS_UNSUBSCRIBE, { threadId: subscribedThreadId })
    subscribedThreadId = null
  }
  ttsStreamPlayer.stop()
  speaking.value = false
  speakingMessageId.value = null
}

/**
 * 按当前状态对齐订阅：自动播报开 + agent 支持 + 有会话 → 订阅且有声；
 * 播报中途关闭开关 → 静音键语义（订阅与服务端合成保持、时间轴继续，仅音量归零，
 * 本条播完由 onDrained 退订）；其余情况（切会话/关能力/无上下文）→ 退订。
 */
function syncSubscription(ttsEnabled: boolean) {
  const inContext = ttsEnabled && !!currentThreadId.value && !!currentAgentId.value
  if (autoBroadcast.value && inContext) {
    subscribe(currentThreadId.value, currentAgentId.value)
    ttsStreamPlayer.setMuted(false)
  } else if (inContext && speaking.value && subscribedThreadId === currentThreadId.value) {
    ttsStreamPlayer.setMuted(true)
  } else {
    unsubscribe()
  }
}

// ==================== 对外 API ====================

function setContext(agentId: string, threadId: string) {
  currentAgentId.value = agentId
  currentThreadId.value = threadId
}

/** 切换自动播报（须在用户手势内调用：开启时顺势解锁音频上下文） */
function toggleAutoBroadcast() {
  autoBroadcast.value = !autoBroadcast.value
  localStorage.setItem(AUTO_BROADCAST_KEY, autoBroadcast.value ? '1' : '0')
  if (autoBroadcast.value) {
    ttsStreamPlayer.unlock()
  }
}

/**
 * 手动朗读一条消息（操作栏按钮，点击即手势可解锁音频）。
 * 未订阅时临时订阅当前 thread；服务端会打断该 thread 进行中的播报。
 */
function speakMessage(messageId: string, markdown: string) {
  if (!currentThreadId.value || !currentAgentId.value) {
    message.info('当前没有会话上下文，无法朗读')
    return
  }
  ttsStreamPlayer.unlock()
  // 手动点播报 = 明确要听：即使全局开关关着（静音）也解除静音（不改开关状态，
  // 播完由 onDrained 按开关状态决定是否退订）
  ttsStreamPlayer.setMuted(false)
  if (subscribedThreadId !== currentThreadId.value) {
    subscribe(currentThreadId.value, currentAgentId.value)
  }
  ttsStreamPlayer.stop()
  speakingMessageId.value = messageId
  ttsApi.broadcast(currentThreadId.value, currentAgentId.value, markdown).catch(() => {
    speakingMessageId.value = null
  })
}

/**
 * 打断当前播报但保持订阅（停止按钮）：本地静音 + 退订/重订让服务端 abort 会话
 */
function interrupt() {
  if (subscribedThreadId) {
    const threadId = subscribedThreadId
    const agentId = currentAgentId.value
    wsHandler()?.sendMessage(WS_MESSAGE_TYPES.TTS_UNSUBSCRIBE, { threadId })
    wsHandler()?.sendMessage(WS_MESSAGE_TYPES.TTS_SUBSCRIBE, { threadId, agentId })
  }
  ttsStreamPlayer.stop()
  speaking.value = false
  speakingMessageId.value = null
}

/** 彻底停止并退订（关开关/切会话/离开页面） */
function stopAll() {
  unsubscribe()
}

/** 本地立即静音（发送新消息时清残余缓冲，订阅与服务端会话不动） */
function localStop() {
  ttsStreamPlayer.stop()
  speaking.value = false
  speakingMessageId.value = null
}

export function useTtsPlayback() {
  return {
    autoBroadcast: computed(() => autoBroadcast.value),
    speaking: computed(() => speaking.value),
    speakingMessageId: computed(() => speakingMessageId.value),
    setContext,
    syncSubscription,
    toggleAutoBroadcast,
    speakMessage,
    interrupt,
    stopAll,
    localStop
  }
}

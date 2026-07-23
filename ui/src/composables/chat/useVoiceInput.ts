import { onBeforeUnmount, ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import { startPcmRecording, type PcmRecorderHandle } from '@/utils/audio/pcmRecorder'
import * as asrApi from '@/api/asr'

export type VoiceInputStatus = 'idle' | 'recording' | 'transcribing'

/**
 * 本次录音的触发入口：click=点击按钮（识别后回填等确认），
 * ptt=长按空格（识别后自动发送），press=移动端按住说话（识别后自动发送）
 */
export type VoiceInputMode = 'click' | 'ptt' | 'press' | null

/**
 * 语音输入聚合状态：单对象沿组件链透传（enabled 来自 agent 绑定，其余来自状态机）
 */
export interface VoiceInputState {
  enabled: boolean
  status: VoiceInputStatus
  seconds: number
  /** 录音实时音量 0~1（驱动波浪条） */
  level: number
  mode: VoiceInputMode
  /** 移动端按住说话的上滑取消预备态（UI 变红提示「松开取消」） */
  cancelIntent: boolean
}

/** 按住说话的手势动作（VoicePressBar 上抛，Chat 页映射到录音原语） */
export type VoicePressAction = 'start' | 'end' | 'cancel' | 'intent-on' | 'intent-off'

/** 长按空格进入录音的判定时长（ms），短于此视为短按、不产生任何动作 */
const PTT_HOLD_MS = 300

/** 按住说话的最短有效时长（ms），短于此视为误触、丢弃并提示 */
const MIN_PRESS_MS = 800

/**
 * 语音输入状态机：idle →(点击/长按空格)→ recording →(点击/松开空格/超时)→ transcribing → idle。
 * 两个入口两种语义：点击按钮=识别后回填输入框等确认（谨慎档）；
 * 长按空格（PTT）=松手即识别并自动发送（快捷档，onPttSend 回调触发）。
 * 录音中按 Esc 取消；窗口失焦/切页自动取消（防止空格 keyup 丢失导致录音挂死）。
 * 空格劫持四门卫：输入区为空（canPtt）、非 IME 组合、非 repeat、焦点在安全区。
 */
export function useVoiceInput(options: {
  agentId: Ref<string>
  onResult: (text: string) => void
  /** PTT 是否允许触发（通常为：已绑 ASR 模型 && 输入区为空 && 非回复中） */
  canPtt?: () => boolean
  /** 快捷档（ptt/press）识别完成回填后的自动发送回调 */
  onAutoSend?: () => void
  maxSeconds?: number
}) {
  const maxSeconds = options.maxSeconds ?? 60

  const status = ref<VoiceInputStatus>('idle')
  const elapsedSeconds = ref(0)
  const level = ref(0)
  const mode = ref<VoiceInputMode>(null)
  const cancelIntent = ref(false)

  let handle: PcmRecorderHandle | null = null
  let timer: ReturnType<typeof setInterval> | null = null
  /** 录音起始时刻（毫秒级，判定按住说话的超短误触） */
  let recordStartedAt = 0
  /** PTT 长按判定定时器（keydown 后 300ms 触发录音） */
  let pttTimer: ReturnType<typeof setTimeout> | null = null
  /** 空格是否仍被按住（定时器触发时校验，防止已松手仍开录） */
  let pttHolding = false

  const clearTimer = () => {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  const clearPttTimer = () => {
    if (pttTimer !== null) {
      clearTimeout(pttTimer)
      pttTimer = null
    }
  }

  const onEscape = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && status.value === 'recording') {
      cancel()
      message.info('已取消语音输入')
    }
  }

  async function start(m: Exclude<VoiceInputMode, null> = 'click') {
    if (status.value !== 'idle') return
    try {
      handle = await startPcmRecording({
        onLevel: (v) => {
          level.value = v
        }
      })
    } catch (e) {
      message.warning((e as Error)?.message || '录音启动失败')
      return
    }
    mode.value = m
    status.value = 'recording'
    elapsedSeconds.value = 0
    level.value = 0
    cancelIntent.value = false
    recordStartedAt = performance.now()
    window.addEventListener('keydown', onEscape)
    timer = setInterval(() => {
      elapsedSeconds.value += 1
      if (elapsedSeconds.value >= maxSeconds) {
        message.info(`已达最长 ${maxSeconds} 秒，自动结束录音`)
        stop()
      }
    }, 1000)
  }

  async function stop() {
    if (status.value !== 'recording' || !handle) return
    const modeWas = mode.value
    clearTimer()
    window.removeEventListener('keydown', onEscape)
    // 按住说话的超短误触：丢弃录音并提示（微信同款「说话时间太短」）
    if (modeWas === 'press' && performance.now() - recordStartedAt < MIN_PRESS_MS) {
      handle.cancel()
      handle = null
      status.value = 'idle'
      mode.value = null
      level.value = 0
      cancelIntent.value = false
      message.info('说话时间太短')
      return
    }
    status.value = 'transcribing'
    try {
      const audio = await handle.stop()
      handle = null
      // null = 整段静音（档 1 预检），不发请求；后端有同逻辑强制兜底
      if (!audio) {
        message.info('未检测到语音，请靠近麦克风重试')
        return
      }
      const res = await asrApi.recognize(options.agentId.value, audio)
      const text = res.data?.data?.trim()
      if (text) {
        options.onResult(text)
        // 快捷档（长按空格/按住说话）：回填后立即自动发送
        if (modeWas === 'ptt' || modeWas === 'press') {
          options.onAutoSend?.()
        }
      } else {
        message.info('未识别到语音内容，请重试')
      }
    } catch (e) {
      // axios 业务错误（R.fail）由全局拦截器提示，此处兜底网络类异常
      const msg = (e as Error)?.message
      if (msg) {
        message.warning('语音识别失败：' + msg)
      }
    } finally {
      status.value = 'idle'
      mode.value = null
      level.value = 0
      cancelIntent.value = false
    }
  }

  function cancel() {
    clearTimer()
    clearPttTimer()
    pttHolding = false
    window.removeEventListener('keydown', onEscape)
    handle?.cancel()
    handle = null
    status.value = 'idle'
    mode.value = null
    level.value = 0
    cancelIntent.value = false
  }

  /** 移动端按住说话：touchmove 越界时置真，录音条变红提示「松开取消」 */
  function setCancelIntent(value: boolean) {
    if (status.value === 'recording') {
      cancelIntent.value = value
    }
  }

  /** 主按钮点击入口：空闲开始录音（谨慎档），录音中结束并识别，识别中忽略 */
  function toggle() {
    if (status.value === 'idle') {
      start('click')
    } else if (status.value === 'recording') {
      stop()
    }
  }

  /**
   * 焦点安全区判定：聊天编辑器或页面空白处才允许空格劫持，
   * 其他输入控件（搜索框等）里空格保持原生行为
   */
  function isFocusSafe(): boolean {
    const el = document.activeElement as HTMLElement | null
    if (!el || el === document.body) return true
    if (el.classList?.contains('chat-input-editor')) return true
    const tag = el.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable) return false
    return true
  }

  const onPttKeydown = (e: KeyboardEvent) => {
    if (e.code !== 'Space' || e.repeat || e.isComposing) return
    if (status.value !== 'idle' || pttTimer !== null) return
    if (!options.canPtt?.()) return
    if (!isFocusSafe()) return
    // 通过全部门卫才劫持：阻止空格上屏，300ms 内松手为短按（无动作），按满进入录音
    e.preventDefault()
    pttHolding = true
    pttTimer = setTimeout(() => {
      pttTimer = null
      if (pttHolding) {
        start('ptt')
      }
    }, PTT_HOLD_MS)
  }

  const onPttKeyup = (e: KeyboardEvent) => {
    if (e.code !== 'Space') return
    pttHolding = false
    if (pttTimer !== null) {
      clearPttTimer()
      return
    }
    if (status.value === 'recording' && mode.value === 'ptt') {
      stop()
    }
  }

  /** 窗口失焦/切页：keyup 可能永远不来，录音中直接取消防挂死 */
  const onWindowBlur = () => {
    clearPttTimer()
    pttHolding = false
    if (status.value === 'recording') {
      cancel()
    }
  }

  window.addEventListener('keydown', onPttKeydown)
  window.addEventListener('keyup', onPttKeyup)
  window.addEventListener('blur', onWindowBlur)
  document.addEventListener('visibilitychange', onWindowBlur)

  onBeforeUnmount(() => {
    cancel()
    window.removeEventListener('keydown', onPttKeydown)
    window.removeEventListener('keyup', onPttKeyup)
    window.removeEventListener('blur', onWindowBlur)
    document.removeEventListener('visibilitychange', onWindowBlur)
  })

  return { status, elapsedSeconds, level, mode, cancelIntent, toggle, start, stop, cancel, setCancelIntent }
}

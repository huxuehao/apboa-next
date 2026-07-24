/**
 * 零依赖 PCM 录音器：getUserMedia + AudioWorklet 采集，停止时重采样到 16kHz
 * 并编码为 16bit 单声道 WAV（后端与各 ASR 供应商的通用格式）。
 * 采集管线与未来流式识别（二期 WebSocket 分帧上行）复用，仅出口不同。
 *
 * @author huxuehao
 */

/** 目标采样率：ASR 通用 16kHz */
const TARGET_SAMPLE_RATE = 16000

/** AudioWorklet 处理器源码（经 Blob URL 注册，避免独立文件的构建路径问题） */
const WORKLET_CODE = `
class PcmCaptureProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0] && inputs[0][0]
    if (channel && channel.length > 0) {
      this.port.postMessage(channel.slice(0))
    }
    return true
  }
}
registerProcessor('pcm-capture', PcmCaptureProcessor)
`

export interface PcmRecorderHandle {
  /**
   * 停止录音并产出 WAV Blob。
   * 返回 null 表示整段录音未检测到语音（静音拒发，档 1）——生成式 ASR
   * 对静音会输出幻觉文本，调用方应提示用户而不发起识别请求。
   */
  stop(): Promise<Blob | null>
  /** 取消录音，丢弃已采集数据 */
  cancel(): void
}

export interface PcmRecorderOptions {
  /** 实时音量回调（0~1，约每 50ms 一次），驱动录音波浪条 */
  onLevel?: (level: number) => void
}

/**
 * 开始录音。必须由用户手势触发（AudioContext 的自动播放策略要求）。
 * 环境不支持（非 HTTPS/localhost、无麦克风、权限拒绝）时抛出带中文提示的 Error。
 */
export async function startPcmRecording(options?: PcmRecorderOptions): Promise<PcmRecorderHandle> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error('当前环境不支持录音（需要 HTTPS 或 localhost）')
  }

  let stream: MediaStream
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
    })
  } catch (e) {
    const name = (e as DOMException)?.name
    if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
      throw new Error('麦克风权限被拒绝，请在浏览器设置中允许')
    }
    if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
      throw new Error('未检测到麦克风设备')
    }
    throw new Error('麦克风启动失败，请检查设备')
  }

  // 用浏览器原生采样率采集（Safari 不保证支持指定 16k），停止时统一重采样
  const audioContext = new AudioContext()
  const workletUrl = URL.createObjectURL(new Blob([WORKLET_CODE], { type: 'application/javascript' }))
  const chunks: Float32Array[] = []
  let released = false

  const release = () => {
    if (released) return
    released = true
    stream.getTracks().forEach((track) => track.stop())
    audioContext.close().catch(() => {})
    URL.revokeObjectURL(workletUrl)
  }

  try {
    await audioContext.audioWorklet.addModule(workletUrl)
    const source = audioContext.createMediaStreamSource(stream)
    const capture = new AudioWorkletNode(audioContext, 'pcm-capture')
    // 实时音量：worklet 每帧已在回传 PCM，顺手累计约 50ms 算一次 RMS（零额外采集成本）
    const levelWindow = Math.floor(audioContext.sampleRate * 0.05)
    let levelSum = 0
    let levelCount = 0
    capture.port.onmessage = (event: MessageEvent<Float32Array>) => {
      chunks.push(event.data)
      if (options?.onLevel) {
        for (let i = 0; i < event.data.length; i++) {
          const s = event.data[i] ?? 0
          levelSum += s * s
        }
        levelCount += event.data.length
        if (levelCount >= levelWindow) {
          const rms = Math.sqrt(levelSum / levelCount)
          options.onLevel(Math.min(1, rms * 6))
          levelSum = 0
          levelCount = 0
        }
      }
    }
    source.connect(capture)
    // worklet 不需要出声，不连接 destination
  } catch (e) {
    release()
    throw new Error('录音初始化失败：' + ((e as Error)?.message || e))
  }

  const sourceSampleRate = audioContext.sampleRate

  return {
    async stop(): Promise<Blob | null> {
      release()
      const merged = mergeChunks(chunks)
      const resampled = resampleLinear(merged, sourceSampleRate, TARGET_SAMPLE_RATE)
      // 档 1（静音拒发）+ 档 2（首尾 trim）：分帧能量判定。
      // 档 3 升级位：此判定层可整体替换为 Silero VAD（如 @ricky0123/vad-web），
      // 输入 PCM、输出语音区间的接口形态不变，调用方与后端均无需改动。
      const voice = analyzeVoiceRange(resampled, TARGET_SAMPLE_RATE)
      if (!voice) {
        return null
      }
      const trimmed = resampled.subarray(voice.start, voice.end)
      return encodeWav(floatToInt16(trimmed), TARGET_SAMPLE_RATE)
    },
    cancel(): void {
      release()
      chunks.length = 0
    }
  }
}

/** 分帧长度（ms） */
const FRAME_MS = 30
/**
 * 前端静音判定阈值：刻意取得比后端（ASR_SILENCE_DB 默认 -40dBFS / 占比 3%）更保守，
 * 只拦「明显没说话」，避免前端常量与后端可调参数打架（用户调松后端时前端不应误杀）；
 * 精细灵敏度的权威旋钮在后端 params。
 */
const SILENCE_DB = -45
const MIN_VOICE_RATIO = 0.02
/** 首尾裁剪保留的语音前后余量（ms），防止裁掉轻声的词头词尾 */
const PAD_MS = 300

/**
 * 分帧 RMS 能量分析：返回首尾语音边界（含 padding，样本下标），全程静音返回 null。
 * 中间的句间停顿一律保留——停顿携带断句信息，硬裁反而伤标点与断句质量。
 */
function analyzeVoiceRange(samples: Float32Array, sampleRate: number): { start: number; end: number } | null {
  const frameSize = Math.floor((sampleRate * FRAME_MS) / 1000)
  if (frameSize <= 0 || samples.length < frameSize) {
    return null
  }
  const frameCount = Math.floor(samples.length / frameSize)
  let voiceFrames = 0
  let firstFrame = -1
  let lastFrame = -1
  for (let i = 0; i < frameCount; i++) {
    let sumSquares = 0
    const base = i * frameSize
    for (let j = 0; j < frameSize; j++) {
      const s = samples[base + j] ?? 0
      sumSquares += s * s
    }
    const rms = Math.sqrt(sumSquares / frameSize)
    const db = 20 * Math.log10(rms + 1e-10)
    if (db > SILENCE_DB) {
      voiceFrames++
      if (firstFrame < 0) firstFrame = i
      lastFrame = i
    }
  }
  if (firstFrame < 0 || voiceFrames / frameCount < MIN_VOICE_RATIO) {
    return null
  }
  const pad = Math.floor((sampleRate * PAD_MS) / 1000)
  return {
    start: Math.max(0, firstFrame * frameSize - pad),
    end: Math.min(samples.length, (lastFrame + 1) * frameSize + pad)
  }
}

function mergeChunks(chunks: Float32Array[]): Float32Array {
  const total = chunks.reduce((sum, c) => sum + c.length, 0)
  const merged = new Float32Array(total)
  let offset = 0
  for (const chunk of chunks) {
    merged.set(chunk, offset)
    offset += chunk.length
  }
  return merged
}

/** 线性插值重采样：对 48k/44.1k → 16k 的降采样，语音场景质量足够 */
function resampleLinear(input: Float32Array, fromRate: number, toRate: number): Float32Array {
  if (fromRate === toRate || input.length === 0) return input
  const ratio = fromRate / toRate
  const outLength = Math.floor(input.length / ratio)
  const output = new Float32Array(outLength)
  for (let i = 0; i < outLength; i++) {
    const pos = i * ratio
    const left = Math.floor(pos)
    const right = Math.min(left + 1, input.length - 1)
    const frac = pos - left
    output[i] = (input[left] ?? 0) * (1 - frac) + (input[right] ?? 0) * frac
  }
  return output
}

function floatToInt16(input: Float32Array): Int16Array {
  const output = new Int16Array(input.length)
  for (let i = 0; i < input.length; i++) {
    const s = Math.max(-1, Math.min(1, input[i] ?? 0))
    output[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }
  return output
}

function encodeWav(samples: Int16Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAscii(view, 36, 'data')
  view.setUint32(40, samples.length * 2, true)
  new Int16Array(buffer, 44).set(samples)
  return new Blob([buffer], { type: 'audio/wav' })
}

function writeAscii(view: DataView, offset: number, text: string): void {
  for (let i = 0; i < text.length; i++) {
    view.setUint8(offset + i, text.charCodeAt(i))
  }
}

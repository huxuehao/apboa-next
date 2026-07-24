/**
 * TTS 流式 PCM 播放器：AudioWorklet 环形队列，无缝衔接连续到达的 PCM 帧。
 *
 * 服务端会话按句产出 PCM（16bit 小端、单声道、采样率由 start 事件声明），
 * 帧到即喂，缓冲空时输出静音等待下一帧——天然吸收网络与合成抖动。
 * iOS/Safari 需要在用户手势内 unlock()（resume AudioContext）。
 *
 * @author huxuehao
 */

/** Worklet 处理器源码（Blob URL 注册，避免独立静态资源与部署路径问题） */
const PROCESSOR_SOURCE = `
class PcmStreamProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.queue = [];
    this.offset = 0;
    this.ended = false;
    this.drainedNotified = false;
    this.port.onmessage = (e) => {
      const d = e.data;
      if (d === 'reset') {
        this.queue = [];
        this.offset = 0;
        this.ended = false;
        this.drainedNotified = false;
      } else if (d === 'end') {
        this.ended = true;
      } else {
        this.queue.push(d);
        this.drainedNotified = false;
      }
    };
  }
  process(_inputs, outputs) {
    const out = outputs[0][0];
    let i = 0;
    while (i < out.length && this.queue.length > 0) {
      const head = this.queue[0];
      const n = Math.min(out.length - i, head.length - this.offset);
      out.set(head.subarray(this.offset, this.offset + n), i);
      i += n;
      this.offset += n;
      if (this.offset >= head.length) {
        this.queue.shift();
        this.offset = 0;
      }
    }
    for (; i < out.length; i++) out[i] = 0;
    if (this.ended && this.queue.length === 0 && !this.drainedNotified) {
      this.drainedNotified = true;
      this.port.postMessage('drained');
    }
    return true;
  }
}
registerProcessor('pcm-stream-processor', PcmStreamProcessor);
`

class TtsStreamPlayer {
  private context: AudioContext | null = null
  private node: AudioWorkletNode | null = null
  private gainNode: GainNode | null = null
  private moduleUrl: string | null = null
  private sampleRate = 24000
  private initPromise: Promise<void> | null = null
  private muted = false
  /**
   * 初始化窗口的帧暂存：服务端首句合成完才发 start，首帧毫秒级紧随其后，
   * 而 addModule 是真异步——node 建成前到达的帧不暂存必丢（首次播报首句必丢）
   */
  private pendingFrames: Float32Array[] = []
  /** 初始化窗口内收到的 end 信号（单句回复：帧与 end 都早于 node 建成） */
  private pendingEnd = false

  /** 缓冲播空回调（end 之后所有帧播完） */
  onDrained: (() => void) | null = null

  /**
   * 用户手势内调用：建立/恢复音频上下文，解锁 iOS 自动播放限制
   */
  unlock() {
    void this.ensure(this.sampleRate).then(() => this.context?.resume())
  }

  /**
   * 按流声明的采样率准备播放链路（采样率变化时重建上下文）。
   * reset（清上一轮残留）后立即回放本轮暂存帧，顺序不可颠倒；
   * 两者置于 resume 之前——port 消息不依赖 context 状态，
   * 无手势时 resume 可能长期挂起，暂存帧不能陪着等
   */
  async configure(sampleRate: number) {
    await this.ensure(sampleRate)
    this.node?.port.postMessage('reset')
    this.flushPending()
    await this.context?.resume()
  }

  /**
   * 喂入一段 PCM16（小端单声道）；node 未就绪时暂存，就绪后按到达序回放
   */
  feed(pcm: ArrayBuffer) {
    const int16 = new Int16Array(pcm)
    const f32 = new Float32Array(int16.length)
    for (let i = 0; i < int16.length; i++) {
      f32[i] = int16[i]! / 32768
    }
    if (!this.node) {
      this.pendingFrames.push(f32)
      return
    }
    this.flushPending()
    this.node.port.postMessage(f32, [f32.buffer])
  }

  /** 流结束：缓冲播完后触发 onDrained；node 未就绪时挂起 end 待回放 */
  markEnd() {
    if (!this.node) {
      this.pendingEnd = true
      return
    }
    this.flushPending()
    this.node.port.postMessage('end')
  }

  /**
   * 静音键语义：时间轴继续走（Worklet 照常消费队列），仅音量归零；
   * 取消静音即从当前进度继续有声
   */
  setMuted(muted: boolean) {
    this.muted = muted
    if (this.gainNode) {
      this.gainNode.gain.value = muted ? 0 : 1
    }
  }

  /** 立即停止并清空缓冲（含未回放的暂存，避免混入下一轮） */
  stop() {
    this.pendingFrames = []
    this.pendingEnd = false
    this.node?.port.postMessage('reset')
  }

  /**
   * 回放初始化窗口内暂存的帧与 end 信号。幂等；feed/markEnd 处的调用
   * 兜底帧先于 start 到达的频道乱序（事件与音频走不同 Redis 频道）
   */
  private flushPending() {
    if (!this.node) return
    for (const f32 of this.pendingFrames) {
      this.node.port.postMessage(f32, [f32.buffer])
    }
    this.pendingFrames = []
    if (this.pendingEnd) {
      this.pendingEnd = false
      this.node.port.postMessage('end')
    }
  }

  private async ensure(sampleRate: number) {
    if (this.context && this.sampleRate === sampleRate && this.node) {
      return
    }
    if (this.context && this.sampleRate !== sampleRate) {
      // 采样率变更：拆除重建（极少发生——换 TTS 引擎才会）
      await this.teardown()
    }
    if (!this.initPromise) {
      this.initPromise = this.init(sampleRate)
    }
    await this.initPromise
  }

  private async init(sampleRate: number) {
    this.sampleRate = sampleRate
    const context = new AudioContext({ sampleRate })
    this.moduleUrl = URL.createObjectURL(new Blob([PROCESSOR_SOURCE], { type: 'application/javascript' }))
    await context.audioWorklet.addModule(this.moduleUrl)
    const node = new AudioWorkletNode(context, 'pcm-stream-processor', {
      numberOfInputs: 0,
      numberOfOutputs: 1,
      outputChannelCount: [1]
    })
    node.port.onmessage = (e) => {
      if (e.data === 'drained') {
        this.onDrained?.()
      }
    }
    const gain = context.createGain()
    gain.gain.value = this.muted ? 0 : 1
    node.connect(gain)
    gain.connect(context.destination)
    this.context = context
    this.node = node
    this.gainNode = gain
  }

  private async teardown() {
    try {
      this.node?.disconnect()
      await this.context?.close()
    } catch { /* 忽略拆除异常 */ }
    if (this.moduleUrl) {
      URL.revokeObjectURL(this.moduleUrl)
      this.moduleUrl = null
    }
    this.context = null
    this.node = null
    this.gainNode = null
    this.initPromise = null
  }
}

/** 全局单例：同时只有一路播报 */
export const ttsStreamPlayer = new TtsStreamPlayer()

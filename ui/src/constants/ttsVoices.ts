/**
 * Qwen（DashScope）TTS 音色候选
 * 模型配置表单与 agent 音色覆盖表单共用。
 * value 即传给阿里 API 的 voice 参数；此处仅收录官方确信的双语音色（中文普通话 + 英文/多语言），
 * 长尾或阿里新增音色经表单「自定义」入口手填 voice 值即可，无需改动此表。
 *
 * @author vaulka
 */
export interface TtsVoiceOption {
  /** 传给阿里 API 的 voice 值 */
  value: string
  /** 音色中文名 */
  name: string
  /** 性别 */
  gender: '女' | '男'
}

/** 默认音色（与后端 DashScopeTtsProvider.DEFAULT_VOICE 保持一致） */
export const DEFAULT_TTS_VOICE = 'Cherry'

/** Qwen3-TTS 双语音色（中文普通话 + 英文 / 多语言） */
export const TTS_VOICES: TtsVoiceOption[] = [
  { value: 'Cherry', name: '芊悦', gender: '女' },
  { value: 'Chelsie', name: '千雪', gender: '女' },
  { value: 'Serena', name: '苏瑶', gender: '女' },
  { value: 'Ethan', name: '晨煦', gender: '男' }
]

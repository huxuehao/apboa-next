import request from '@/utils/request'
import type { ApiResponse } from '@/types'

/**
 * 手动朗读（历史消息等）：走流式会话通道，音频经已订阅的 WS 音频流返回。
 * POST /api/runtime/tts/broadcast
 */
export function broadcast(threadId: string, agentId: string, text: string) {
  return request.post<ApiResponse<void>>('/api/runtime/tts/broadcast', { threadId, agentId, text })
}

/** 本地克隆 TTS 的音色目录条目（私有协议） */
export interface CloneVoice {
  name: string
  refAudio: string
  refText: string
}

/**
 * 拉取本地克隆 TTS 的音色列表（OPEN_AI TTS 配置下拉用）：后端转发 {baseUrl}/voices。
 * GET /api/runtime/tts/voices?baseUrl=xxx
 */
export function voices(baseUrl: string) {
  return request.get<ApiResponse<CloneVoice[]>>('/api/runtime/tts/voices', { params: { baseUrl } })
}
